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
  @DisplayName("starts without token, registers SlackService and logs WARN")
  void startsWithoutToken() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(SlackService.class);
          assertThat(context.getBeansOfType(MethodsClient.class)).isEmpty();
          assertThat(warnMessages()).anyMatch(m -> m.contains("not configured"));
        });
  }

  @Test
  @DisplayName("starts with a valid token, registers MethodsClient and logs no warning")
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

  @Test
  @DisplayName("starts with a blank token, treated as missing and logs WARN")
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
