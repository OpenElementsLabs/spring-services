package com.openelements.spring.base.services.email;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        @DisplayName("sends a plain-text email with correct fields")
        void sendsPlainTextEmail() {
            emailService.sendEmail("recipient@example.com", "Test Subject", "Hello World");

            final SimpleMailMessage message = capturedMessage();
            assertThat(message.getTo()).containsExactly("recipient@example.com");
            assertThat(message.getFrom()).isEqualTo("noreply@example.com");
            assertThat(message.getSubject()).isEqualTo("Test Subject");
            assertThat(message.getText()).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("includes display name when from-name is configured")
        void includesDisplayName() {
            properties.setFromName("My App");

            emailService.sendEmail("recipient@example.com", "Subject", "Body");

            final SimpleMailMessage message = capturedMessage();
            assertThat(message.getFrom()).isEqualTo("My App <noreply@example.com>");
        }

        @Test
        @DisplayName("uses plain address when no display name is configured")
        void plainAddressWhenNoDisplayName() {
            emailService.sendEmail("recipient@example.com", "Subject", "Body");

            assertThat(capturedMessage().getFrom()).isEqualTo("noreply@example.com");
        }

        @Test
        @DisplayName("uses plain address when from-name is blank")
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
        @DisplayName("sends to the user's stored email address")
        void sendsToUserEmail() {
            final UserEntity user = new UserEntity();
            user.setEmail("user@example.com");

            emailService.sendEmail(user, "Subject", "Body");

            assertThat(capturedMessage().getTo()).containsExactly("user@example.com");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when user has null email")
        void throwsWhenUserEmailNull() {
            final UserEntity user = new UserEntity();
            user.setEmail(null);

            assertThatThrownBy(() -> emailService.sendEmail(user, "Subject", "Body"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no email");
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when user has blank email")
        void throwsWhenUserEmailBlank() {
            final UserEntity user = new UserEntity();
            user.setEmail("");

            assertThatThrownBy(() -> emailService.sendEmail(user, "Subject", "Body"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no email");
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("rejects null user")
        void rejectsNullUser() {
            assertThatThrownBy(() -> emailService.sendEmail((UserEntity) null, "Subject", "Body"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("sendEmail — graceful degradation")
    class GracefulDegradation {

        @Test
        @DisplayName("throws EmailException when no mail sender is available")
        void throwsWhenMailSenderUnavailable() {
            final EmailService unconfigured = new EmailService(provider(null), properties);

            assertThatThrownBy(() -> unconfigured.sendEmail("a@b.c", "Subject", "Body"))
                    .isInstanceOf(EmailException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("throws EmailException when from address is not configured")
        void throwsWhenFromMissing() {
            properties.setFrom(null);

            assertThatThrownBy(() -> emailService.sendEmail("a@b.c", "Subject", "Body"))
                    .isInstanceOf(EmailException.class)
                    .hasMessageContaining("Sender address");
        }

        @Test
        @DisplayName("throws EmailException when from address is blank")
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

        @Test
        @DisplayName("wraps MailException as EmailException with cause")
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
        @DisplayName("rejects null recipient address")
        void rejectsNullTo() {
            assertThatThrownBy(() -> emailService.sendEmail((String) null, "Subject", "Body"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null subject")
        void rejectsNullSubject() {
            assertThatThrownBy(() -> emailService.sendEmail("a@b.c", null, "Body"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null body")
        void rejectsNullBody() {
            assertThatThrownBy(() -> emailService.sendEmail("a@b.c", "Subject", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
