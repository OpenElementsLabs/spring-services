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

/**
 * Spring-Boot startup tests for {@link EmailConfig}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The graceful-degradation contract: applications must boot whether or not SMTP is wired.
 * Spring Boot's {@link MailSenderAutoConfiguration} only registers a {@link JavaMailSender} when
 * {@code spring.mail.host} is present, and {@link EmailService} must cope with its absence:
 *
 * <ul>
 *   <li>Without {@code spring.mail.host}, the context boots and registers an {@link EmailService}
 *       bean (so injection at autowire sites still succeeds), no {@link JavaMailSender} bean is
 *       present, and a WARN log advertises the misconfiguration to operators.
 *   <li>With {@code spring.mail.host} set, both beans are registered and no WARN is emitted.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Spring Boot's {@link ApplicationContextRunner} drives {@link EmailConfig} alongside the
 * real {@link MailSenderAutoConfiguration} in isolation, with the property varied per test. A
 * Logback {@link ListAppender} is attached to the {@link EmailService} logger before each test
 * and detached after; WARN events are filtered out for assertion.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. Both auto-configurations run as real Spring beans inside the
 * runner. No SMTP server is contacted because no test actually calls {@code sendEmail} — that
 * surface belongs to {@link EmailServiceTest}; this test only verifies bean wiring and the
 * startup WARN.
 */
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
  @DisplayName("Without spring.mail.host the context still boots: EmailService is registered, no JavaMailSender bean exists, and a WARN is logged at startup.")
  void startsWithoutMailHost() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(EmailService.class);
          assertThat(context.getBeansOfType(JavaMailSender.class)).isEmpty();
          assertThat(warnMessages()).anyMatch(m -> m.contains("not available"));
        });
  }

  @Test
  @DisplayName("With spring.mail.host set, both EmailService and JavaMailSender are wired and no WARN is emitted.")
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
