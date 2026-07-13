/**
 * Reusable email sending integration.
 *
 * <p>This package provides {@link com.openelements.spring.base.services.email.EmailService}, a thin
 * {@code @Service} wrapper around Spring's {@link org.springframework.mail.javamail.JavaMailSender}
 * that sends plain-text emails via SMTP.
 *
 * <h2>Configuration</h2>
 *
 * <p>SMTP connection settings use Spring Boot's standard {@code spring.mail.*} properties.
 * Service-specific settings (sender address and optional display name) are bound from {@code
 * open-elements.email.*} via {@link com.openelements.spring.base.services.email.EmailProperties}.
 *
 * <p>If no {@code JavaMailSender} is auto-configured (because {@code spring.mail.host} is not set)
 * the {@code EmailService} bean is still registered, but a warning is logged at startup and every
 * call to {@code sendEmail} throws {@link
 * com.openelements.spring.base.services.email.EmailException}.
 *
 * <h2>Scope</h2>
 *
 * <p>The service is intentionally minimal: plain-text only, no attachments, no CC/BCC, no
 * templating, no retries.
 */
package com.openelements.spring.base.services.email;
