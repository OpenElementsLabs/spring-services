package com.openelements.spring.base.services.email;

import com.openelements.spring.base.services.user.UserEntity;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text emails via SMTP using Spring's {@link JavaMailSender}.
 *
 * <p>The sender address is read from {@code open-elements.email.from}; an optional display name
 * comes from {@code open-elements.email.from-name}. SMTP connection settings use Spring Boot's
 * standard {@code spring.mail.*} properties.
 *
 * <p>If no {@link JavaMailSender} is available (i.e. {@code spring.mail.host} is not configured)
 * the bean is still registered, but every call to {@code sendEmail} fails fast with an {@link
 * EmailException}.
 */
@Service
@EnableConfigurationProperties(EmailProperties.class)
public class EmailService {

  private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

  private static final String MAIL_NOT_CONFIGURED =
      "Mail sending is not configured (set 'spring.mail.host')";

  private static final String FROM_NOT_CONFIGURED =
      "Sender address is not configured (set 'open-elements.email.from')";

  @Nullable private final JavaMailSender mailSender;

  private final EmailProperties properties;

  public EmailService(
      final ObjectProvider<JavaMailSender> mailSenderProvider, final EmailProperties properties) {
    this.mailSender = mailSenderProvider.getIfAvailable();
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  private static String formatFrom(final String address, @Nullable final String displayName) {
    if (displayName == null || displayName.isBlank()) {
      return address;
    }
    try {
      return new InternetAddress(address, displayName, "UTF-8").toString();
    } catch (final UnsupportedEncodingException e) {
      throw new EmailException("Invalid sender address '" + address + "'", e);
    }
  }

  @PostConstruct
  void warnIfNotConfigured() {
    if (mailSender == null) {
      LOG.warn(
          "Email sending is not available. No JavaMailSender bean is registered "
              + "(set 'spring.mail.host' to enable SMTP). Calls to EmailService.sendEmail "
              + "will fail.");
    }
  }

  /**
   * Sends a plain-text email to the given address.
   *
   * @throws EmailException if mail sending is not configured, the sender address is missing, or
   *     SMTP delivery fails
   * @throws NullPointerException if any argument is {@code null}
   */
  public void sendEmail(
      @NonNull final String to, @NonNull final String subject, @NonNull final String body) {
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(body, "body must not be null");
    if (mailSender == null) {
      throw new EmailException(MAIL_NOT_CONFIGURED);
    }
    final String from = properties.getFrom();
    if (from == null || from.isBlank()) {
      throw new EmailException(FROM_NOT_CONFIGURED);
    }

    final SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(formatFrom(from, properties.getFromName()));
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);

    try {
      mailSender.send(message);
    } catch (final MailException e) {
      throw new EmailException("Failed to send email to '" + to + "'", e);
    }
  }

  /**
   * Sends a plain-text email to the address stored on {@code user}.
   *
   * @throws IllegalArgumentException if the user has no email address
   * @throws EmailException if mail sending is not configured or SMTP delivery fails
   * @throws NullPointerException if any argument is {@code null}
   */
  public void sendEmail(
      @NonNull final UserEntity user, @NonNull final String subject, @NonNull final String body) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(body, "body must not be null");
    final String email = user.getEmail();
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("User has no email address");
    }
    sendEmail(email, subject, body);
  }
}
