package com.openelements.spring.base.slack;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.slack.SlackService;
import com.openelements.spring.base.services.slack.events.SlackEventDeduplicator;
import com.openelements.spring.base.services.slack.events.SlackEventsEndpoint;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;

/**
 * Activation tests for {@link SlackEventsAutoConfiguration}.
 *
 * <p>The feature opens a public path, so the interesting behaviour is that nothing happens without
 * {@code open-elements.slack.events.enabled=true}, and that an enabled feature without a signing
 * secret still starts but says so.
 */
@DisplayName("SlackEventsAutoConfiguration")
@ExtendWith(OutputCaptureExtension.class)
class SlackEventsAutoConfigurationTest {

  private static final String SECRET_WARNING = "open-elements.slack.events.signing-secret";

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SlackAutoConfiguration.class,
                  SlackEventsAutoConfiguration.class,
                  TaskExecutionAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class));

  private final WebApplicationContextRunner enabled =
      runner.withPropertyValues(
          "open-elements.slack.events.enabled=true",
          "open-elements.slack.events.signing-secret=test-signing-secret");

  @Test
  @DisplayName("Registers nothing and needs no secret while open-elements.slack.events.enabled is unset")
  void inertByDefault(final CapturedOutput output) {
    runner.run(
        context -> {
          assertThat(context)
              .hasNotFailed()
              .doesNotHaveBean(SlackEventsEndpoint.class)
              .doesNotHaveBean(SlackEventDeduplicator.class)
              .doesNotHaveBean("slackEventsRouterFunction")
              .doesNotHaveBean("slackEventsSecurityFilterChain")
              .doesNotHaveBean(SlackEventsAutoConfiguration.EXECUTOR_BEAN_NAME);
          assertThat(output).doesNotContain(SECRET_WARNING);
        });
  }

  @Test
  @DisplayName("Registers endpoint, route, filter chain, executor and deduplicator when enabled")
  void activatesWhenEnabled(final CapturedOutput output) {
    enabled.run(
        context -> {
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(SlackEventsEndpoint.class)
              .hasSingleBean(SlackEventDeduplicator.class)
              .hasBean("slackEventsRouterFunction")
              .hasBean("slackEventsSecurityFilterChain")
              .hasBean(SlackEventsAutoConfiguration.EXECUTOR_BEAN_NAME)
              .hasBean(SlackEventsAutoConfiguration.CLOCK_BEAN_NAME);
          assertThat(output).doesNotContain(SECRET_WARNING);
        });
  }

  @Test
  @DisplayName("Starts without a signing secret and logs a warning that names the property")
  void startsWithoutSecret(final CapturedOutput output) {
    runner
        .withPropertyValues("open-elements.slack.events.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(SlackEventsEndpoint.class);
              assertThat(output).contains(SECRET_WARNING);
            });
  }

  @Test
  @DisplayName("Treats a blank signing secret as missing")
  void startsWithBlankSecret(final CapturedOutput output) {
    runner
        .withPropertyValues(
            "open-elements.slack.events.enabled=true",
            "open-elements.slack.events.signing-secret=   ")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(SlackEventsEndpoint.class);
              assertThat(output).contains(SECRET_WARNING);
            });
  }

  @Test
  @DisplayName("Receives without a Slack token, independent of the sending side")
  void receivesWithoutToken() {
    enabled.run(
        context ->
            assertThat(context)
                .hasNotFailed()
                .hasSingleBean(SlackService.class)
                .hasSingleBean(SlackEventsEndpoint.class));
  }

  @Test
  @DisplayName("Uses the application's executor when it declares slackEventsTaskExecutor")
  void applicationExecutorWins() {
    enabled
        .withUserConfiguration(ApplicationExecutor.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(SlackEventsAutoConfiguration.EXECUTOR_BEAN_NAME))
                  .isSameAs(context.getBean(ApplicationExecutor.class).executor);
            });
  }

  @Test
  @DisplayName("Leaves Spring Boot's application task executor in place")
  void keepsApplicationTaskExecutor() {
    enabled.run(
        context ->
            assertThat(context)
                .hasNotFailed()
                .hasBean("applicationTaskExecutor")
                .hasBean(SlackEventsAutoConfiguration.EXECUTOR_BEAN_NAME));
  }

  @Test
  @DisplayName("Leaves injection of an Executor by type to the application's beans")
  void executorIsNoAutowireCandidate() {
    enabled
        .withUserConfiguration(InjectsExecutor.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(InjectsExecutor.class).executor)
                  .isSameAs(context.getBean("applicationTaskExecutor"));
            });
  }

  @Test
  @DisplayName("Leaves injection of a Clock by type to the application's own clock")
  void clockIsNoAutowireCandidate() {
    enabled
        .withUserConfiguration(ApplicationClock.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              final ApplicationClock application = context.getBean(ApplicationClock.class);
              assertThat(application.injected).isSameAs(application.clock);
            });
  }

  @Test
  @DisplayName("Registers nothing outside a servlet web application")
  void inertOutsideWebApplication() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SlackEventsAutoConfiguration.class))
        .withPropertyValues(
            "open-elements.slack.events.enabled=true",
            "open-elements.slack.events.signing-secret=test-signing-secret")
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(SlackEventsEndpoint.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class InjectsExecutor {

    Executor executor;

    @Bean
    String usesExecutor(final Executor executor) {
      this.executor = executor;
      return "uses executor";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ApplicationClock {

    final Clock clock = Clock.systemDefaultZone();

    Clock injected;

    @Bean
    Clock applicationClock() {
      return clock;
    }

    @Bean
    String usesClock(final Clock clock) {
      this.injected = clock;
      return "uses clock";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ApplicationExecutor {

    final Executor executor = new SyncTaskExecutor();

    @Bean(SlackEventsAutoConfiguration.EXECUTOR_BEAN_NAME)
    Executor slackEventsTaskExecutor() {
      return executor;
    }
  }
}
