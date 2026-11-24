package com.openelements.spring.base.slack;

import static com.openelements.spring.base.slack.SlackEventsWebTest.EVENTS;
import static com.openelements.spring.base.slack.SlackEventsWebTest.NOW;
import static com.openelements.spring.base.slack.SlackEventsWebTest.SECRET;
import static com.openelements.spring.base.slack.SlackEventsWebTest.signed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.services.slack.events.MutableClock;
import com.openelements.spring.base.services.slack.events.SlackFixtures;
import com.openelements.spring.base.services.slack.events.SlackMessageReceivedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Delivery on the feature's real executor: Slack's acknowledgement must not wait for listeners, and
 * a failing listener must not reach the response.
 *
 * <p>Deterministic without sleeping: the slow listener is held by a latch the test opens itself, and
 * the failing listener's log entry is awaited with a bounded timeout.
 */
@DisplayName("Slack events delivered asynchronously")
@ExtendWith(OutputCaptureExtension.class)
class SlackEventsAsyncWebTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SlackEventsAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  JacksonAutoConfiguration.class))
          .withPropertyValues(
              "open-elements.slack.events.enabled=true",
              "open-elements.slack.events.signing-secret=" + SECRET);

  @Test
  @DisplayName("Answers 200 while a listener is still running")
  void slowListenerDoesNotDelayResponse() {
    runner
        .withUserConfiguration(TestClock.class, BlockingListener.class)
        .run(
            context -> {
              final BlockingListener listener = context.getBean(BlockingListener.class);
              try {
                mvc(context)
                    .perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                    .andExpect(status().isOk());

                assertThat(listener.entered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
                assertThat(listener.delivered.getCount()).isEqualTo(1);
              } finally {
                listener.release.countDown();
              }
              assertThat(listener.delivered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
              assertThat(listener.threadName).startsWith("slack-events-");
            });
  }

  @Test
  @DisplayName("Answers 200 and logs the exception when a listener fails")
  void failingListenerDoesNotAffectResponse(final CapturedOutput output) {
    runner
        .withUserConfiguration(TestClock.class, FailingListener.class)
        .run(
            context -> {
              mvc(context)
                  .perform(signed(EVENTS, SlackFixtures.load("message.json"), NOW))
                  .andExpect(status().isOk());

              await()
                  .atMost(TIMEOUT)
                  .untilAsserted(
                      () ->
                          assertThat(output)
                              .contains("A listener failed for Slack event Ev0PV52K21")
                              .contains(FailingListener.MESSAGE));
            });
  }

  private static MockMvc mvc(final org.springframework.context.ApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
  }

  @Configuration(proxyBeanMethods = false)
  static class TestClock {

    @Bean(SlackEventsAutoConfiguration.CLOCK_BEAN_NAME)
    MutableClock slackEventsClock() {
      return new MutableClock(Instant.ofEpochSecond(NOW));
    }
  }

  /** Blocks until the test releases it, then reports delivery. */
  static class BlockingListener {

    final CountDownLatch entered = new CountDownLatch(1);

    final CountDownLatch release = new CountDownLatch(1);

    final CountDownLatch delivered = new CountDownLatch(1);

    volatile String threadName = "";

    @EventListener
    void on(final SlackMessageReceivedEvent event) throws InterruptedException {
      threadName = Thread.currentThread().getName();
      entered.countDown();
      if (release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        delivered.countDown();
      }
    }
  }

  /** Always throws. */
  static class FailingListener {

    static final String MESSAGE = "listener failed on purpose";

    @EventListener
    void on(final SlackMessageReceivedEvent event) {
      throw new IllegalStateException(MESSAGE);
    }
  }
}
