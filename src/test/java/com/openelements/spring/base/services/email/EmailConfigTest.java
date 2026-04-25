package com.openelements.spring.base.services.email;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("EmailConfig — application startup")
class EmailConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(EmailConfig.class, MailSenderAutoConfiguration.class))
          .withUserConfiguration(EmailService.class);

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void attachLogAppender() {
    final Logger emailServiceLogger = (Logger) LoggerFactory.getLogger(EmailService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    emailServiceLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    final Logger emailServiceLogger = (Logger) LoggerFactory.getLogger(EmailService.class);
    emailServiceLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("starts without spring.mail.host, registers EmailService and logs WARN")
  void startsWithoutMailHost() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(EmailService.class);
          assertThat(context.getBeansOfType(JavaMailSender.class)).isEmpty();
          assertThat(warnMessages()).anyMatch(m -> m.contains("not available"));
        });
  }

  @Test
  @DisplayName("starts with spring.mail.host set, registers JavaMailSender and logs no warning")
  void startsWithMailHost() {
    contextRunner
        .withPropertyValues("spring.mail.host=smtp.example.com")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(EmailService.class);
              assertThat(context).hasSingleBean(JavaMailSender.class);
              assertThat(warnMessages()).noneMatch(m -> m.contains("not available"));
            });
  }

  private java.util.List<String> warnMessages() {
    return logAppender.list.stream()
        .filter(e -> e.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
