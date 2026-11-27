package com.openelements.spring.base.slack;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.services.slack.authorization.InMemorySlackUserTokenStore;
import com.openelements.spring.base.services.slack.authorization.JdbcSlackUserTokenStore;
import com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationEndpoint;
import com.openelements.spring.base.services.slack.authorization.SlackUserTokenStore;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import com.slack.api.Slack;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The user authorization endpoints through HTTP, behind Spring Security, with Slack's {@code
 * oauth.v2.access} served by a local WireMock server.
 *
 * <p>The application side is a stand-in for spring-services' default chain: no matcher, every
 * request must be authenticated, and an anonymous request gets {@code 401}. Tokens go to an
 * in-memory store declared by the application, except in the tests about the default store.
 */
@DisplayName("Slack user authorization over HTTP")
@ExtendWith(OutputCaptureExtension.class)
class SlackUserAuthorizationWebTest {

  static final String CLIENT_ID = "1234.5678";

  static final String CLIENT_SECRET = "web-test-client-secret";

  static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  static final String BASE_URL = "https://app.example.com";

  static final String AUTHORIZE = "/slack/oauth/authorize";

  static final String CALLBACK = "/slack/oauth/callback";

  static final String USER_TOKEN = "xoxp-web-test-user-token";

  /** The default {@code openelements.problem-details.type-base}. */
  private static final String TYPE_BASE = "https://open-elements.com/problems/spring-services/";

  static WireMockServer slackApi;

  private final WebApplicationContextRunner base =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SlackAutoConfiguration.class,
                  SlackUserAuthorizationAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  ProblemDetailsAutoConfiguration.class,
                  TraceAutoConfiguration.class))
          .withUserConfiguration(Application.class);

  private final WebApplicationContextRunner disabled = base.withUserConfiguration(WithStore.class);

  private final WebApplicationContextRunner notConfigured =
      disabled.withPropertyValues("open-elements.slack.user-authorization.enabled=true");

  private final WebApplicationContextRunner enabled =
      notConfigured.withPropertyValues(
          "open-elements.slack.user-authorization.client-id=" + CLIENT_ID,
          "open-elements.slack.user-authorization.client-secret=" + CLIENT_SECRET,
          "open-elements.slack.user-authorization.public-base-url=" + BASE_URL,
          "open-elements.slack.user-authorization.token-encryption-key=" + KEY);

  @BeforeAll
  static void startSlackApi() {
    slackApi = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    slackApi.start();
  }

  @AfterAll
  static void stopSlackApi() {
    slackApi.stop();
  }

  @BeforeEach
  void resetSlackApi() {
    slackApi.resetAll();
  }

  @Nested
  @DisplayName("Activation")
  class Activation {

    @Test
    @DisplayName("Leaves the paths to the default chain while the feature is disabled")
    void disabledLeavesPathsToDefaultChain() {
      run(
          disabled,
          (mvc, context) -> {
            assertThat(context).doesNotHaveBean(SlackUserAuthorizationEndpoint.class);
            assertThat(context).doesNotHaveBean("slackUserAuthorizationSecurityFilterChain");
            mvc.perform(get(AUTHORIZE)).andExpect(status().isUnauthorized());
          });
    }

    @Test
    @DisplayName("Registers the default JDBC store when a database is available, without a warning")
    void defaultStore(final CapturedOutput output) {
      run(
          enabledWithoutStore().withUserConfiguration(WithDatabase.class),
          (mvc, context) -> {
            assertThat(context).hasSingleBean(SlackUserAuthorizationEndpoint.class);
            assertThat(context).hasBean("slackUserAuthorizationSecurityFilterChain");
            assertThat(context.getBean(SlackUserTokenStore.class))
                .isInstanceOf(JdbcSlackUserTokenStore.class);
          });
      assertThat(output).doesNotContain("Editing Slack messages of users is enabled, but not");
    }

    @Test
    @DisplayName("Uses a store of the application and needs no database then")
    void applicationStore() {
      run(
          enabled,
          (mvc, context) ->
              assertThat(context.getBean(SlackUserTokenStore.class))
                  .isInstanceOf(InMemorySlackUserTokenStore.class));
    }

    @Test
    @DisplayName("Fails start-up without a store and without a database, naming both options")
    void noStoreNoDatabase() {
      enabledWithoutStore()
          .run(
              context ->
                  assertThat(context)
                      .hasFailed()
                      .getFailure()
                      .rootCause()
                      .hasMessageContaining("DataSource")
                      .hasMessageContaining("SlackUserTokenStore"));
    }

    @Test
    @DisplayName("Starts without configuration, warns once and answers 503 on both paths")
    void notConfigured(final CapturedOutput output) {
      run(
          notConfigured,
          (mvc, context) -> {
            for (final String path : List.of(AUTHORIZE, CALLBACK)) {
              mvc.perform(get(path))
                  .andExpect(status().isServiceUnavailable())
                  .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                  .andExpect(
                      jsonPath("$.type")
                          .value(TYPE_BASE + "slack-user-authorization-not-configured"));
            }
          });
      assertThat(output)
          .contains("open-elements.slack.user-authorization.client-id")
          .contains("open-elements.slack.user-authorization.client-secret")
          .contains("open-elements.slack.user-authorization.public-base-url")
          .contains("open-elements.slack.user-authorization.token-encryption-key");
    }

    @Test
    @DisplayName("Treats an invalid encryption key as not configured without printing it")
    void invalidKey(final CapturedOutput output) {
      final String invalidKey = "c2hvcnQta2V5LW9mLXNpeHRlZW4h";
      run(
          enabled.withPropertyValues(
              "open-elements.slack.user-authorization.token-encryption-key=" + invalidKey),
          (mvc, context) -> mvc.perform(get(AUTHORIZE)).andExpect(status().isServiceUnavailable()));
      assertThat(output).contains("invalid").doesNotContain(invalidKey);
    }

    private WebApplicationContextRunner enabledWithoutStore() {
      return base.withPropertyValues(
          "open-elements.slack.user-authorization.enabled=true",
          "open-elements.slack.user-authorization.client-id=" + CLIENT_ID,
          "open-elements.slack.user-authorization.client-secret=" + CLIENT_SECRET,
          "open-elements.slack.user-authorization.public-base-url=" + BASE_URL,
          "open-elements.slack.user-authorization.token-encryption-key=" + KEY);
    }
  }

  @Nested
  @DisplayName("Start of the authorization")
  class Authorize {

    @Test
    @DisplayName("Redirects to Slack with the user scope only and sets the state cookie")
    void redirectsToSlack() {
      run(
          enabled,
          (mvc, context) -> {
            final MockHttpServletResponse response =
                mvc.perform(get(AUTHORIZE)).andExpect(status().isFound()).andReturn().getResponse();

            final URI location = URI.create(response.getHeader(HttpHeaders.LOCATION));
            final var query =
                UriComponentsBuilder.fromUri(location).build(true).getQueryParams().toSingleValueMap();
            assertThat(location.getScheme() + "://" + location.getHost() + location.getPath())
                .isEqualTo("https://slack.com/oauth/v2/authorize");
            assertThat(decode(query.get("client_id"))).isEqualTo(CLIENT_ID);
            assertThat(decode(query.get("user_scope"))).isEqualTo("chat:write");
            assertThat(decode(query.get("redirect_uri"))).isEqualTo(BASE_URL + CALLBACK);
            assertThat(query).doesNotContainKey("scope").doesNotContainKey("team");
            final String state = decode(query.get("state"));
            assertThat(state).hasSizeGreaterThanOrEqualTo(43);
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(SlackUserAuthorizationEndpoint.STATE_COOKIE + "=" + state + ";")
                .contains("Path=/slack/oauth")
                .contains("Max-Age=600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
          });
    }

    @Test
    @DisplayName("Preselects the workspace configured for Slack events")
    void preselectsWorkspace() {
      run(
          enabled.withPropertyValues("open-elements.slack.events.team-id=T1"),
          (mvc, context) ->
              assertThat(
                      mvc.perform(get(AUTHORIZE))
                          .andReturn()
                          .getResponse()
                          .getHeader(HttpHeaders.LOCATION))
                  .contains("team=T1"));
    }

    @Test
    @DisplayName("Uses a new state for every authorization")
    void newStateEachTime() {
      run(
          enabled,
          (mvc, context) ->
              assertThat(state(mvc)).isNotEqualTo(state(mvc)));
    }
  }

  @Nested
  @DisplayName("Callback")
  class Callback {

    @Test
    @DisplayName("Exchanges the code and stores the token of the user Slack names")
    void storesToken() {
      stubAccess(accessResponse("T1", "U1", "chat:write", null));
      run(
          enabled,
          (mvc, context) -> {
            final String state = state(mvc);

            final MockHttpServletResponse response =
                mvc.perform(callback(state, state).param("user", "U2"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(
                        content().string("Slack authorization complete. You can close this window."))
                    .andReturn()
                    .getResponse();

            assertThat(store(context).find("T1", "U1")).contains(USER_TOKEN);
            assertThat(store(context).find("T1", "U2")).isEmpty();
            assertCookieCleared(response);
            slackApi.verify(
                postRequestedFor(urlEqualTo("/api/oauth.v2.access"))
                    .withBasicAuth(new BasicCredentials(CLIENT_ID, CLIENT_SECRET))
                    .withFormParam("code", equalTo("the-code"))
                    .withFormParam("redirect_uri", equalTo(BASE_URL + CALLBACK)));
          });
    }

    @Test
    @DisplayName("Replaces the token when the same user authorizes again")
    void replacesToken() {
      run(
          enabled,
          (mvc, context) -> {
            store(context).save("T1", "U1", "xoxp-old");
            stubAccess(accessResponse("T1", "U1", "chat:write", null));
            final String state = state(mvc);

            mvc.perform(callback(state, state)).andExpect(status().isOk());

            assertThat(store(context).find("T1", "U1")).contains(USER_TOKEN);
            assertThat(store(context).size()).isEqualTo(1);
          });
    }

    @Test
    @DisplayName("Rejects a cancelled authorization without calling Slack")
    void cancelled() {
      run(
          enabled,
          (mvc, context) -> {
            final String state = state(mvc);

            expectProblem(
                mvc.perform(callback(state, state).param("error", "access_denied"))
                    .andReturn()
                    .getResponse(),
                HttpStatus.BAD_REQUEST,
                "slack-user-authorization-rejected");
            assertThat(store(context).size()).isZero();
            assertThat(slackApi.getAllServeEvents()).isEmpty();
          });
    }

    @Test
    @DisplayName("Rejects a state that differs from the cookie, a missing state and a missing cookie")
    void stateMismatch() {
      run(
          enabled,
          (mvc, context) -> {
            final String state = state(mvc);
            final List<MockHttpServletResponse> responses =
                List.of(
                    mvc.perform(callback(state, "other-state")).andReturn().getResponse(),
                    mvc.perform(callback(null, state)).andReturn().getResponse(),
                    mvc.perform(callback(state, null)).andReturn().getResponse());

            for (final MockHttpServletResponse response : responses) {
              expectProblem(response, HttpStatus.BAD_REQUEST, "slack-user-authorization-rejected");
              assertCookieCleared(response);
            }
            assertThat(store(context).size()).isZero();
            assertThat(slackApi.getAllServeEvents()).isEmpty();
          });
    }

    @Test
    @DisplayName("Answers 502 when Slack refuses the code")
    void exchangeFails() {
      stubAccess("{\"ok\":false,\"error\":\"invalid_code\"}");
      expectFailedCallback();
    }

    @Test
    @DisplayName("Answers 502 when the user token lacks chat:write")
    void scopeMissing() {
      stubAccess(accessResponse("T1", "U1", "users:read", null));
      expectFailedCallback();
    }

    @Test
    @DisplayName("Answers 502 when Slack issues no user token")
    void noUserToken() {
      stubAccess("{\"ok\":true,\"team\":{\"id\":\"T1\"}}");
      expectFailedCallback();
    }

    @Test
    @DisplayName("Refuses a token from a Slack app with token rotation and says so")
    void tokenRotation(final CapturedOutput output) {
      stubAccess(accessResponse("T1", "U1", "chat:write", "xoxe-refresh"));
      expectFailedCallback();
      assertThat(output).contains("token rotation").doesNotContain("xoxe-refresh");
    }

    @Test
    @DisplayName("Rejects an authorization for another workspace")
    void foreignWorkspace() {
      stubAccess(accessResponse("T2", "U1", "chat:write", null));
      run(
          enabled.withPropertyValues("open-elements.slack.events.team-id=T1"),
          (mvc, context) -> {
            final String state = state(mvc);

            expectProblem(
                mvc.perform(callback(state, state)).andReturn().getResponse(),
                HttpStatus.BAD_REQUEST,
                "slack-user-authorization-rejected");
            assertThat(store(context).size()).isZero();
          });
    }

    @Test
    @DisplayName("Answers with the bare status while problem details are disabled")
    void problemDetailsDisabled() {
      run(
          enabled.withPropertyValues("openelements.problem-details.enabled=false"),
          (mvc, context) ->
              mvc.perform(callback("a", "b"))
                  .andExpect(status().isBadRequest())
                  .andExpect(content().string("")));
    }

    @Test
    @DisplayName("Never logs the code, the state, the client secret, the token or the key")
    void secretsNotLogged(final CapturedOutput output) {
      stubAccess(accessResponse("T1", "U1", "chat:write", null));
      final String[] state = new String[1];
      run(
          enabled,
          (mvc, context) -> {
            state[0] = state(mvc);
            mvc.perform(callback(state[0], state[0])).andExpect(status().isOk());
            mvc.perform(callback(state[0], "other-state")).andExpect(status().isBadRequest());
          });
      stubAccess("{\"ok\":false,\"error\":\"invalid_code\"}");
      expectFailedCallback();

      assertThat(output)
          .contains("Stored the Slack user token of user U1 in workspace T1")
          .doesNotContain("the-code")
          .doesNotContain(state[0])
          .doesNotContain(CLIENT_SECRET)
          .doesNotContain(USER_TOKEN)
          .doesNotContain(KEY);
    }

    private void expectFailedCallback() {
      run(
          enabled,
          (mvc, context) -> {
            final String state = state(mvc);

            expectProblem(
                mvc.perform(callback(state, state)).andReturn().getResponse(),
                HttpStatus.BAD_GATEWAY,
                "slack-user-authorization-failed");
            assertThat(store(context).size()).isZero();
          });
    }
  }

  @Nested
  @DisplayName("Security chain")
  class SecurityChain {

    @Test
    @DisplayName("Opens only GET on the two paths")
    void onlyGet() {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(post(AUTHORIZE)).andExpect(status().isForbidden());
            mvc.perform(post(CALLBACK)).andExpect(status().isForbidden());
          });
    }

    @Test
    @DisplayName("Opens no other path")
    void noOtherPath() {
      run(
          enabled,
          (mvc, context) -> {
            mvc.perform(get("/slack/oauth")).andExpect(status().isUnauthorized());
            mvc.perform(get("/slack/oauth/other")).andExpect(status().isUnauthorized());
          });
    }

    @Test
    @DisplayName("Serves a custom path, derives redirect URI and cookie path, and leaves the default closed")
    void customPath() {
      run(
          enabled.withPropertyValues(
              "open-elements.slack.user-authorization.path=/integrations/slack-auth"),
          (mvc, context) -> {
            final MockHttpServletResponse response =
                mvc.perform(get("/integrations/slack-auth/authorize"))
                    .andExpect(status().isFound())
                    .andReturn()
                    .getResponse();
            assertThat(decode(response.getHeader(HttpHeaders.LOCATION)))
                .contains("redirect_uri=" + BASE_URL + "/integrations/slack-auth/callback");
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/integrations/slack-auth");
            mvc.perform(get(AUTHORIZE)).andExpect(status().isUnauthorized());
          });
    }
  }

  private static String state(final MockMvc mvc) throws Exception {
    final String location =
        mvc.perform(get(AUTHORIZE)).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
    return decode(
        UriComponentsBuilder.fromUriString(location)
            .build(true)
            .getQueryParams()
            .getFirst("state"));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      callback(final String state, final String cookie) {
    final var request = get(CALLBACK).param("code", "the-code");
    if (state != null) {
      request.param("state", state);
    }
    if (cookie != null) {
      request.cookie(new Cookie(SlackUserAuthorizationEndpoint.STATE_COOKIE, cookie));
    }
    return request;
  }

  private static void expectProblem(
      final MockHttpServletResponse response, final HttpStatus status, final String slug)
      throws Exception {
    assertThat(response.getStatus()).isEqualTo(status.value());
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getContentAsString()).contains("\"type\":\"" + TYPE_BASE + slug + "\"");
  }

  private static void assertCookieCleared(final MockHttpServletResponse response) {
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .startsWith(SlackUserAuthorizationEndpoint.STATE_COOKIE + "=;")
        .contains("Max-Age=0");
  }

  private static void stubAccess(final String body) {
    slackApi.stubFor(WireMock.post(urlEqualTo("/api/oauth.v2.access")).willReturn(okJson(body)));
  }

  private static String accessResponse(
      final String teamId, final String userId, final String scope, final String refreshToken) {
    return """
        {"ok":true,"app_id":"A1","team":{"id":"%s","name":"Team"},
         "authed_user":{"id":"%s","scope":"%s","token_type":"user","access_token":"%s"%s}}
        """
        .formatted(
            teamId,
            userId,
            scope,
            USER_TOKEN,
            refreshToken == null ? "" : ",\"refresh_token\":\"" + refreshToken + "\"");
  }

  private static String decode(final String value) {
    return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static InMemorySlackUserTokenStore store(final AssertableWebApplicationContext context) {
    return context.getBean(InMemorySlackUserTokenStore.class);
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

    /** Points the Slack SDK at the local WireMock server. */
    @Bean
    Slack slack() {
      final com.slack.api.SlackConfig config = new com.slack.api.SlackConfig();
      config.setMethodsEndpointUrlPrefix(slackApi.baseUrl() + "/api/");
      return Slack.getInstance(config);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class WithStore {

    @Bean
    InMemorySlackUserTokenStore slackUserTokenStore() {
      return new InMemorySlackUserTokenStore();
    }
  }

  /** A JDBC client whose database is never contacted: the tests only check which store exists. */
  @Configuration(proxyBeanMethods = false)
  static class WithDatabase {

    @Bean
    JdbcClient jdbcClient() {
      return JdbcClient.create(new DriverManagerDataSource("jdbc:postgresql://localhost:1/none"));
    }
  }
}
