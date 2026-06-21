package com.openelements.spring.base.services.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.services.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Mockito-style unit tests for {@link EmailService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two overloads of {@code sendEmail} and their guard rails:
 *
 * <ul>
 *   <li>{@code sendEmail(String to, ...)} composes a {@link SimpleMailMessage} with the correct
 *       {@code to}, {@code from}, {@code subject} and {@code text}, hands it to {@link
 *       JavaMailSender#send(SimpleMailMessage)}, and applies the optional display-name formatting
 *       (RFC-style {@code "Name <addr>"}) only when {@code open-elements.email.from-name} is set
 *       and non-blank.
 *   <li>{@code sendEmail(UserEntity user, ...)} reads the email from the user row and delegates;
 *       a user with {@code null}/blank email throws {@link IllegalArgumentException}
 *       <em>before</em> any mail-sender call.
 *   <li>Graceful degradation: missing {@link JavaMailSender} or missing/blank {@code
 *       open-elements.email.from} both surface as {@link EmailException} with descriptive
 *       messages.
 *   <li>SMTP delivery failure ({@link MailSendException}) is wrapped as {@link EmailException}
 *       with the original cause preserved.
 *   <li>{@code null} arguments are rejected with {@link NullPointerException} on the public
 *       contract — caller bugs surface immediately.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Mockito, no Spring context. {@link EmailService} is instantiated directly
 * with a fake {@link ObjectProvider} and a plain {@link EmailProperties} POJO populated per test.
 * The composed {@link SimpleMailMessage} is captured via {@link ArgumentCaptor} so each field
 * (to, from, subject, body) can be asserted independently.
 *
 * <p><b>Mock-Audit.</b> Two mocks:
 *
 * <ul>
 *   <li>{@code mock(JavaMailSender.class)} — Spring's SMTP gateway. A real {@code JavaMailSender}
 *       would open a TCP connection to an SMTP server; this surface is verified end-to-end only
 *       at deployment time. The mock lets us (a) capture the composed message for field-level
 *       assertions and (b) inject a {@link MailSendException} for the error-wrapping test.
 *   <li>{@code mock(ObjectProvider.class)} — Spring's optional-bean wrapper. Mocking lets a
 *       single test toggle between the "configured" and "not configured" states without a Spring
 *       context. The real wiring is covered by {@link EmailConfigTest}.
 * </ul>
 *
 * <p>{@link EmailProperties} is used as a real POJO, not mocked — it has no behaviour beyond
 * field accessors, so a mock would only obscure the test setup.
 */
@DisplayName("EmailService")
class EmailServiceTest {

  private JavaMailSender mailSender;
  private EmailProperties properties;
  private EmailService emailService;

  @SuppressWarnings("unchecked")
  private static ObjectProvider<JavaMailSender> provider(final JavaMailSender sender) {
    final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(sender);
    return provider;
  }

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    properties = new EmailProperties();
    properties.setFrom("noreply@example.com");
    emailService = new EmailService(provider(mailSender), properties);
  }

  private SimpleMailMessage capturedMessage() {
    final ArgumentCaptor<SimpleMailMessage> captor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("sendEmail(String, String, String) — happy path")
  class SendByAddress {

    @Test
    @DisplayName("sendEmail(to, subject, body) composes a SimpleMailMessage with the correct to / from / subject / text and hands it to JavaMailSender.")
    void sendsPlainTextEmail() {
      emailService.sendEmail("recipient@example.com", "Test Subject", "Hello World");

      final SimpleMailMessage message = capturedMessage();
      assertThat(message.getTo()).containsExactly("recipient@example.com");
      assertThat(message.getFrom()).isEqualTo("noreply@example.com");
      assertThat(message.getSubject()).isEqualTo("Test Subject");
      assertThat(message.getText()).isEqualTo("Hello World");
    }

    /**
     * Pins the RFC-style {@code "Display Name <addr@example.com>"} formatting that downstream
     * mail clients use to render the sender. The {@link jakarta.mail.internet.InternetAddress}
     * formatting is what's being delegated to here; the test confirms the wiring, not the RFC.
     */
    @Test
    @DisplayName("With from-name configured, sendEmail produces an RFC-style \"Display Name <addr>\" From header.")
    void includesDisplayName() {
      properties.setFromName("My App");

      emailService.sendEmail("recipient@example.com", "Subject", "Body");

      final SimpleMailMessage message = capturedMessage();
      assertThat(message.getFrom()).isEqualTo("My App <noreply@example.com>");
    }

    @Test
    @DisplayName("With no from-name configured, the From header is the bare address — no empty \"<addr>\" wrapper.")
    void plainAddressWhenNoDisplayName() {
      emailService.sendEmail("recipient@example.com", "Subject", "Body");

      assertThat(capturedMessage().getFrom()).isEqualTo("noreply@example.com");
    }

    /**
     * Symmetric to the WARN-on-blank-token check in {@link
     * com.openelements.spring.base.services.slack.SlackConfigTest}: an environment that exports
     * {@code OPEN_ELEMENTS_EMAIL_FROM_NAME="   "} must not emit an RFC header containing only
     * whitespace as the display name.
     */
    @Test
    @DisplayName("A whitespace-only from-name is treated as missing — the From header is the bare address.")
    void plainAddressWhenDisplayNameBlank() {
      properties.setFromName("   ");

      emailService.sendEmail("recipient@example.com", "Subject", "Body");

      assertThat(capturedMessage().getFrom()).isEqualTo("noreply@example.com");
    }
  }

  @Nested
  @DisplayName("sendEmail(UserEntity, String, String)")
  class SendByUser {

    @Test
    @DisplayName("sendEmail(UserEntity, ...) sends to the email address stored on the user row.")
    void sendsToUserEmail() {
      final UserEntity user = new UserEntity();
      user.setEmail("user@example.com");

      emailService.sendEmail(user, "Subject", "Body");

      assertThat(capturedMessage().getTo()).containsExactly("user@example.com");
    }

    @Test
    @DisplayName("A user with email=null throws IllegalArgumentException before JavaMailSender.send is touched.")
    void throwsWhenUserEmailNull() {
      final UserEntity user = new UserEntity();
      user.setEmail(null);

      assertThatThrownBy(() -> emailService.sendEmail(user, "Subject", "Body"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no email");
      verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("A user with email=\"\" (blank) throws IllegalArgumentException before JavaMailSender.send is touched.")
    void throwsWhenUserEmailBlank() {
      final UserEntity user = new UserEntity();
      user.setEmail("");

      assertThatThrownBy(() -> emailService.sendEmail(user, "Subject", "Body"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no email");
      verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendEmail((UserEntity) null, ...) throws NullPointerException — no JavaMailSender.send call.")
    void rejectsNullUser() {
      assertThatThrownBy(() -> emailService.sendEmail((UserEntity) null, "Subject", "Body"))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("sendEmail — graceful degradation")
  class GracefulDegradation {

    @Test
    @DisplayName("With no JavaMailSender available, sendEmail fails fast with EmailException carrying the \"not configured\" hint.")
    void throwsWhenMailSenderUnavailable() {
      final EmailService unconfigured = new EmailService(provider(null), properties);

      assertThatThrownBy(() -> unconfigured.sendEmail("a@b.c", "Subject", "Body"))
          .isInstanceOf(EmailException.class)
          .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("With open-elements.email.from=null, sendEmail throws EmailException pointing to the missing sender property.")
    void throwsWhenFromMissing() {
      properties.setFrom(null);

      assertThatThrownBy(() -> emailService.sendEmail("a@b.c", "Subject", "Body"))
          .isInstanceOf(EmailException.class)
          .hasMessageContaining("Sender address");
    }

    @Test
    @DisplayName("A whitespace-only open-elements.email.from is treated as missing — sendEmail throws EmailException.")
    void throwsWhenFromBlank() {
      properties.setFrom("   ");

      assertThatThrownBy(() -> emailService.sendEmail("a@b.c", "Subject", "Body"))
          .isInstanceOf(EmailException.class)
          .hasMessageContaining("Sender address");
    }
  }

  @Nested
  @DisplayName("sendEmail — error handling")
  class ErrorHandling {

    /**
     * Confirms SMTP failures preserve their cause for operator triage: a {@link
     * MailSendException} ({code MailException} subclass thrown on SMTP timeout, auth failure,
     * etc.) is wrapped as {@link EmailException} with the original {@code MailException} set as
     * {@code cause}.
     */
    @Test
    @DisplayName("A MailException from JavaMailSender (e.g. SMTP timeout) is wrapped as EmailException with the original MailException as cause.")
    void wrapsMailException() {
      final MailSendException cause = new MailSendException("smtp timeout");
      doThrow(cause).when(mailSender).send(any(SimpleMailMessage.class));

      assertThatThrownBy(() -> emailService.sendEmail("a@b.c", "Subject", "Body"))
          .isInstanceOf(EmailException.class)
          .hasCause(cause);
    }
  }

  @Nested
  @DisplayName("sendEmail — input validation")
  class InputValidation {

    @Test
    @DisplayName("sendEmail((String) null, ...) throws NullPointerException — no JavaMailSender.send call.")
    void rejectsNullTo() {
      assertThatThrownBy(() -> emailService.sendEmail((String) null, "Subject", "Body"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sendEmail(to, null, body) throws NullPointerException — no JavaMailSender.send call.")
    void rejectsNullSubject() {
      assertThatThrownBy(() -> emailService.sendEmail("a@b.c", null, "Body"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sendEmail(to, subject, null) throws NullPointerException — no JavaMailSender.send call.")
    void rejectsNullBody() {
      assertThatThrownBy(() -> emailService.sendEmail("a@b.c", "Subject", null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
