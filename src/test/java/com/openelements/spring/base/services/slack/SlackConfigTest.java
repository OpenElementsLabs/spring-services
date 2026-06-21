package com.openelements.spring.base.services.slack;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.slack.api.methods.MethodsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Spring-Boot startup tests for {@link SlackConfig}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The graceful-degradation contract of the Slack auto-configuration: applications that depend
 * on {@code spring-services} must boot whether or not {@code open-elements.slack.token} is
 * configured. Specifically:
 *
 * <ul>
 *   <li>Without a token, the context still wires a {@link SlackService} bean but registers
 *       <em>no</em> {@link MethodsClient}, and a WARN log is emitted at startup so the missing
 *       configuration is visible in operator logs.
 *   <li>With a valid token, both {@link SlackService} and {@link MethodsClient} are present and
 *       no warning is emitted.
 *   <li>A blank-string token (whitespace) is treated as missing — guards against {@code
 *       open-elements.slack.token=""} in environment files silently producing a broken client.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Spring Boot's {@link ApplicationContextRunner} drives the auto-configuration in isolation,
 * with property values varied per test. WARN-level log messages are captured by attaching a
 * Logback {@link ListAppender} to the {@link SlackService} logger before each test and detaching
 * it after — the captured list is filtered down to WARN events for assertions.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. {@code SlackConfig} and {@code SlackService} run as real
 * beans inside the context runner; assertions inspect the real bean graph and the real log
 * stream. The Slack HTTP API is never contacted because we only verify that the bean is
 * registered, not that messages are sent (that surface belongs to {@link SlackServiceTest}).
 */
@DisplayName("SlackConfig — application startup")
class SlackConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SlackConfig.class))
          .withUserConfiguration(SlackService.class);

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void attachLogAppender() {
    final Logger slackServiceLogger = (Logger) LoggerFactory.getLogger(SlackService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    slackServiceLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    final Logger slackServiceLogger = (Logger) LoggerFactory.getLogger(SlackService.class);
    slackServiceLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("With no open-elements.slack.token, the context boots: SlackService is registered, no MethodsClient bean exists, and a WARN is logged.")
  void startsWithoutToken() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(SlackService.class);
          assertThat(context.getBeansOfType(MethodsClient.class)).isEmpty();
          assertThat(warnMessages()).anyMatch(m -> m.contains("not configured"));
        });
  }

  @Test
  @DisplayName("With a valid open-elements.slack.token, both SlackService and MethodsClient are wired and no WARN is emitted.")
  void startsWithValidToken() {
    contextRunner
        .withPropertyValues("open-elements.slack.token=xoxb-test-token")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(SlackService.class);
              assertThat(context).hasSingleBean(MethodsClient.class);
              assertThat(warnMessages()).noneMatch(m -> m.contains("not configured"));
            });
  }

  /**
   * Guards against the common operational pitfall where an environment exports {@code
   * OPEN_ELEMENTS_SLACK_TOKEN="   "} — Spring would bind a non-null whitespace string, which a
   * naive null-check would accept. The config must reject blank tokens the same way it rejects
   * missing tokens.
   */
  @Test
  @DisplayName("A whitespace-only open-elements.slack.token is treated as missing — no MethodsClient is wired and a WARN is logged.")
  void startsWithBlankToken() {
    contextRunner
        .withPropertyValues("open-elements.slack.token=   ")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(SlackService.class);
              assertThat(context.getBeansOfType(MethodsClient.class)).isEmpty();
              assertThat(warnMessages()).anyMatch(m -> m.contains("not configured"));
            });
  }

  private java.util.List<String> warnMessages() {
    return logAppender.list.stream()
        .filter(e -> e.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
