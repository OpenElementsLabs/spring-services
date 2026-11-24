package com.openelements.spring.base.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.services.slack.events.MutableClock;
import com.openelements.spring.base.services.slack.events.SlackEventsEndpoint;
import com.openelements.spring.base.services.slack.events.SlackFixtures;
import com.openelements.spring.base.services.slack.events.SlackMessageKind;
import com.openelements.spring.base.services.slack.events.SlackMessageReceivedEvent;
import com.openelements.spring.base.services.slack.events.SlackRequestSigner;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The events endpoint through HTTP, behind Spring Security.
 *
 * <p>The application side is a stand-in for spring-services' default chain: no matcher, every
 * request must be authenticated, and an anonymous request gets {@code 401}. That is the situation
 * the feature's own chain exists for — without it Slack could not reach the endpoint.
 *
 * <p>The listeners run on a synchronous executor here, so a published event can be asserted right
 * after the request; the real executor is covered by {@code SlackEventsAsyncWebTest}. Time comes
 * from a {@link MutableClock}, so timestamps and the de-duplication window are deterministic.
 */
@DisplayName("Slack events over HTTP")
class SlackEventsWebTest {

  static final String SECRET = "web-test-signing-secret";

  static final String EVENTS = "/slack/events";

  static final long NOW = 1_800_000_000L;

  /** The default {@code openelements.problem-details.type-base}. */
  private static final String TYPE_BASE = "https://open-elements.com/problems/spring-services/";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final WebApplicationContextRunner disabled =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SlackEventsAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  ProblemDetailsAutoConfiguration.class,
                  TraceAutoConfiguration.class))
          .withUserConfiguration(Application.class);

  private final WebApplicationContextRunner notConfigured =
      disabled.withPropertyValues("open-elements.slack.events.enabled=true");

  private final WebApplicationContextRunner enabled =
      notConfigured.withPropertyValues("open-elements.slack.events.signing-secret=" + SECRET);

  @Nested
  @DisplayName("Activation and security chain")
  class Activation {

    @Test
    @DisplayName("Leaves the path to the default chain while the feature is disabled")
    void disabledLeavesPathToDefaultChain() {
      run(
          disabled,
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                .andExpect(status().isUnauthorized());
            assertThat(recorded(context)).isEmpty();
          });
    }

    @Test
    @DisplayName("Rejects every request, including url_verification, without a signing secret")
    void notConfiguredRejectsEverything() {
      run(
          notConfigured,
          (mvc, context) -> {
            for (final String fixture : List.of("url-verification.json", "message.json")) {
              mvc.perform(signed(EVENTS, SlackFixtures.load(fixture), NOW))
                  .andExpect(status().isServiceUnavailable())
                  .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                  .andExpect(jsonPath("$.type").value(TYPE_BASE + "slack-events-not-configured"));
            }
            assertThat(recorded(context)).isEmpty();
          });
    }

    @Test
    @DisplayName("Opens only POST on the events path")
    void opensOnlyPostOnEventsPath() {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(get(EVENTS)).andExpect(status().isForbidden());
            mvc.perform(signed("/other", SlackFixtures.load("message.json"), NOW))
                .andExpect(status().isUnauthorized());
          });
    }

    @Test
    @DisplayName("Serves a custom path and leaves the default path closed")
    void customPath() {
      run(
          enabled.withPropertyValues("open-elements.slack.events.path=/integrations/slack"),
          (mvc, context) -> {
            mvc.perform(signed("/integrations/slack", SlackFixtures.load("message.json"), NOW))
                .andExpect(status().isOk());
            mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                .andExpect(status().isUnauthorized());
            assertThat(recorded(context)).hasSize(1);
          });
    }
  }

  @Nested
  @DisplayName("Signature verification")
  @ExtendWith(OutputCaptureExtension.class)
  class Signature {

    @Test
    @DisplayName("Accepts a body signed with the configured secret and a current timestamp")
    void acceptsValidSignature() {
      run(
          enabled,
          (mvc, context) ->
              mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                  .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("Accepts a timestamp exactly five minutes old")
    void acceptsTimestampAtBoundary() {
      run(
          enabled,
          (mvc, context) ->
              mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW - 300))
                  .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("Rejects a body signed with another secret")
    void rejectsWrongSecret() {
      final byte[] body = SlackFixtures.load("message.json");
      assertRejected(
          request(EVENTS, body, Long.toString(NOW), SlackRequestSigner.sign("other", NOW, body)));
    }

    @Test
    @DisplayName("Rejects a body changed after signing")
    void rejectsTamperedBody() {
      final byte[] body = SlackFixtures.load("message.json");
      final String signature = SlackRequestSigner.sign(SECRET, NOW, body);
      final byte[] tampered =
          new String(body, StandardCharsets.UTF_8)
              .replace("Hello world", "Hello World")
              .getBytes(StandardCharsets.UTF_8);
      assertRejected(request(EVENTS, tampered, Long.toString(NOW), signature));
    }

    @Test
    @DisplayName("Rejects a validly signed request with a stale or future timestamp")
    void rejectsStaleAndFutureTimestamps() {
      assertRejected(signed(EVENTS, SlackFixtures.load("message.json"), NOW - 301));
      assertRejected(signed(EVENTS, SlackFixtures.load("message.json"), NOW + 301));
    }

    @Test
    @DisplayName("Rejects missing headers and a timestamp that is not a number")
    void rejectsMissingAndMalformedHeaders() {
      final byte[] body = SlackFixtures.load("message.json");
      final String signature = SlackRequestSigner.sign(SECRET, NOW, body);
      assertRejected(request(EVENTS, body, Long.toString(NOW), null));
      assertRejected(request(EVENTS, body, null, signature));
      assertRejected(request(EVENTS, body, "yesterday", signature));
    }

    @Test
    @DisplayName("Rejects an unsigned body that is no JSON with 401, not with a parse error")
    void verifiesBeforeParsing() {
      assertRejected(
          request(EVENTS, "{not json".getBytes(StandardCharsets.UTF_8), Long.toString(NOW), "v0=00"));
    }

    @Test
    @DisplayName("Rejects a body larger than 1 MiB with 413")
    void rejectsOversizedBody() {
      final byte[] body = new byte[SlackEventsEndpoint.MAX_BODY_BYTES + 1];
      Arrays.fill(body, (byte) 'a');
      run(
          enabled,
          (mvc, context) ->
              mvc.perform(signed(EVENTS, body, NOW))
                  .andExpect(status().is(HttpStatus.CONTENT_TOO_LARGE.value()))
                  .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                  .andExpect(jsonPath("$.type").value(TYPE_BASE + "slack-event-too-large")));
    }

    @Test
    @DisplayName("Rejects an event of another workspace or another app")
    void rejectsForeignWorkspaceAndApp() {
      run(
          enabled.withPropertyValues("open-elements.slack.events.team-id=T_OTHER"),
          (mvc, context) -> {
            expectRejected(mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW)));
            assertThat(recorded(context)).isEmpty();
          });
      run(
          enabled.withPropertyValues("open-elements.slack.events.app-id=A_OTHER"),
          (mvc, context) -> {
            expectRejected(mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW)));
            assertThat(recorded(context)).isEmpty();
          });
    }

    @Test
    @DisplayName("Logs a rejection at WARN without body, signature or secret")
    void rejectionsDoNotLeakData(final CapturedOutput output) {
      final byte[] body = SlackFixtures.load("message.json");
      final String signature = SlackRequestSigner.sign("other-secret", NOW, body);
      assertRejected(request(EVENTS, body, Long.toString(NOW), signature));

      assertThat(output).contains("WARN").contains("Rejected Slack event request");
      assertThat(output)
          .doesNotContain("Hello world")
          .doesNotContain(signature)
          .doesNotContain(SECRET);
    }

    private void assertRejected(final MockHttpServletRequestBuilder request) {
      run(
          enabled,
          (mvc, context) -> {
            expectRejected(mvc.perform(request));
            assertThat(recorded(context)).isEmpty();
          });
    }

    private void expectRejected(final org.springframework.test.web.servlet.ResultActions result)
        throws Exception {
      result
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(TYPE_BASE + "slack-signature-invalid"))
          .andExpect(jsonPath("$.detail").doesNotExist());
    }
  }

  @Nested
  @DisplayName("URL verification")
  class UrlVerification {

    @Test
    @DisplayName("Echoes the challenge")
    void echoesChallenge() {
      run(
          enabled,
          (mvc, context) ->
              mvc.perform(signed(EVENTS, SlackFixtures.load("url-verification.json"), NOW))
                  .andExpect(status().isOk())
                  .andExpect(content().string("abc123")));
    }

    @Test
    @DisplayName("Echoes the challenge although team-id and app-id are configured")
    void echoesChallengeWithWorkspaceRestriction() {
      run(
          enabled.withPropertyValues(
              "open-elements.slack.events.team-id=T061EG9R6",
              "open-elements.slack.events.app-id=A0PNCHHK2"),
          (mvc, context) ->
              mvc.perform(signed(EVENTS, SlackFixtures.load("url-verification.json"), NOW))
                  .andExpect(status().isOk())
                  .andExpect(content().string("abc123")));
    }

    @Test
    @DisplayName("Answers a signed body that is no JSON with 400")
    void rejectsSignedNonJson() {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, "{not json".getBytes(StandardCharsets.UTF_8), NOW))
                .andExpect(status().isBadRequest());
            assertThat(recorded(context)).isEmpty();
          });
    }
  }

  @Nested
  @DisplayName("Message events")
  class Messages {

    @Test
    @DisplayName("Publishes a channel message with its values")
    void publishesChannelMessage() {
      run(
          enabled.withPropertyValues(
              "open-elements.slack.events.team-id=T061EG9R6",
              "open-elements.slack.events.app-id=A0PNCHHK2"),
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
            assertThat(recorded(context))
                .containsExactly(
                    new SlackMessageReceivedEvent(
                        SlackMessageKind.MESSAGE,
                        "Ev0PV52K21",
                        "T061EG9R6",
                        "C2147483705",
                        "channel",
                        "U2147483697",
                        "Hello world",
                        "1355517523.000005",
                        null,
                        Instant.ofEpochSecond(1355517523)));
          });
    }

    @Test
    @DisplayName("Publishes events while team-id and app-id are configured blank")
    void blankRestrictionsAcceptEvents() {
      run(
          enabled.withPropertyValues(
              "open-elements.slack.events.team-id=", "open-elements.slack.events.app-id="),
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                .andExpect(status().isOk());
            assertThat(recorded(context)).hasSize(1);
          });
    }

    @Test
    @DisplayName("Publishes a mention as APP_MENTION")
    void publishesMention() {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, SlackFixtures.load("app-mention.json"), NOW))
                .andExpect(status().isOk());
            assertThat(recorded(context))
                .singleElement()
                .extracting(SlackMessageReceivedEvent::kind)
                .isEqualTo(SlackMessageKind.APP_MENTION);
          });
    }

    @Test
    @DisplayName("Publishes the thread_ts of a reply in a thread")
    void publishesThreadReply() {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, SlackFixtures.load("thread-reply.json"), NOW))
                .andExpect(status().isOk());
            assertThat(recorded(context))
                .singleElement()
                .extracting(SlackMessageReceivedEvent::threadTs)
                .isEqualTo("1355517523.000005");
          });
    }

    @Test
    @DisplayName("Acknowledges a bot message without publishing it")
    void dropsBotMessage() {
      assertAcknowledgedWithoutEvent(SlackFixtures.load("bot-message.json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"message_changed", "message_deleted", "channel_join"})
    @DisplayName("Acknowledges a message with a subtype without publishing it")
    void dropsSubtypes(final String subtype) {
      final ObjectNode envelope = (ObjectNode) JSON.readTree(SlackFixtures.load("message.json"));
      ((ObjectNode) envelope.get("event")).put("subtype", subtype);
      assertAcknowledgedWithoutEvent(JSON.writeValueAsBytes(envelope));
    }

    @Test
    @DisplayName("Acknowledges other event types and unknown request types without publishing")
    void ignoresOtherTypes() {
      assertAcknowledgedWithoutEvent(SlackFixtures.load("reaction-added.json"));
      assertAcknowledgedWithoutEvent(
          "{\"type\":\"app_rate_limited\"}".getBytes(StandardCharsets.UTF_8));
    }

    private void assertAcknowledgedWithoutEvent(final byte[] body) {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(signed(EVENTS, body, NOW)).andExpect(status().isOk());
            assertThat(recorded(context)).isEmpty();
          });
    }
  }

  @Nested
  @DisplayName("De-duplication")
  class Deduplication {

    @Test
    @DisplayName("Delivers a retried event once")
    void deliversRetryOnce() {
      run(
          enabled,
          (mvc, context) -> {
            final byte[] body = SlackFixtures.load("message.json");
            mvc.perform(signed(EVENTS, body, NOW)).andExpect(status().isOk());
            mvc.perform(signed(EVENTS, body, NOW).header("X-Slack-Retry-Num", "1"))
                .andExpect(status().isOk());
            assertThat(recorded(context)).hasSize(1);
          });
    }

    @Test
    @DisplayName("Delivers an event id again once it is more than an hour old")
    void forgetsAfterAnHour() {
      run(
          enabled,
          (mvc, context) -> {
            final byte[] body = SlackFixtures.load("message.json");
            mvc.perform(signed(EVENTS, body, NOW)).andExpect(status().isOk());
            final Duration later = Duration.ofHours(1).plusSeconds(1);
            context.getBean(MutableClock.class).advance(later);
            mvc.perform(signed(EVENTS, body, NOW + later.toSeconds())).andExpect(status().isOk());
            assertThat(recorded(context)).hasSize(2);
          });
    }
  }

  @Nested
  @DisplayName("Problem details disabled")
  class ProblemDetailsDisabled {

    @Test
    @DisplayName("Keeps the status of every rejection, with an empty body")
    void keepsStatus() {
      run(
          enabled.withPropertyValues("openelements.problem-details.enabled=false"),
          (mvc, context) -> {
            mvc.perform(request(EVENTS, SlackFixtures.load("message.json"), null, null))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
            final byte[] tooLarge = new byte[SlackEventsEndpoint.MAX_BODY_BYTES + 1];
            mvc.perform(signed(EVENTS, tooLarge, NOW))
                .andExpect(status().is(HttpStatus.CONTENT_TOO_LARGE.value()))
                .andExpect(content().string(""));
          });
      run(
          notConfigured.withPropertyValues("openelements.problem-details.enabled=false"),
          (mvc, context) ->
              mvc.perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                  .andExpect(status().isServiceUnavailable())
                  .andExpect(content().string("")));
    }
  }

  static MockHttpServletRequestBuilder signed(
      final String path, final byte[] body, final long timestamp) {
    return request(
        path, body, Long.toString(timestamp), SlackRequestSigner.sign(SECRET, timestamp, body));
  }

  static MockHttpServletRequestBuilder request(
      final String path,
      final byte[] body,
      final @org.jspecify.annotations.Nullable String timestamp,
      final @org.jspecify.annotations.Nullable String signature) {
    final MockHttpServletRequestBuilder request =
        post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    if (timestamp != null) {
      request.header("X-Slack-Request-Timestamp", timestamp);
    }
    if (signature != null) {
      request.header("X-Slack-Signature", signature);
    }
    return request;
  }

  private static List<SlackMessageReceivedEvent> recorded(
      final AssertableWebApplicationContext context) {
    return context.getBean(Recorder.class).events;
  }

  private static void run(final WebApplicationContextRunner runner, final MvcAssertion assertion) {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          final MockMvc mvc =
              MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                  .apply(springSecurity())
                  .build();
          assertion.accept(mvc, context);
        });
  }

  @FunctionalInterface
  interface MvcAssertion {
    void accept(MockMvc mvc, AssertableWebApplicationContext context) throws Exception;
  }

  /** Records every published message. */
  static class Recorder {

    final List<SlackMessageReceivedEvent> events = new CopyOnWriteArrayList<>();

    @EventListener
    void on(final SlackMessageReceivedEvent event) {
      events.add(event);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Application {

    /** Stand-in for the default JWT chain: everything needs authentication, anonymous gets 401. */
    @Bean
    @Order(2)
    SecurityFilterChain defaultChain(final HttpSecurity http) throws Exception {
      http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .exceptionHandling(
              handling ->
                  handling.authenticationEntryPoint(
                      new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
          .csrf(csrf -> csrf.disable());
      return http.build();
    }

    @Bean(SlackEventsAutoConfiguration.CLOCK_BEAN_NAME)
    MutableClock slackEventsClock() {
      return new MutableClock(Instant.ofEpochSecond(NOW));
    }

    @Bean(SlackEventsAutoConfiguration.EXECUTOR_BEAN_NAME)
    Executor slackEventsTaskExecutor() {
      return new SyncTaskExecutor();
    }

    @Bean
    Recorder recorder() {
      return new Recorder();
    }
  }
}
