# Behaviors: Email Sending Service

## Sending Email by Address

### Sends a plain-text email successfully

- **Given** the SMTP server is configured and `open-elements.email.from` is set
- **When** `sendEmail("recipient@example.com", "Test Subject", "Hello World")` is called
- **Then** a `SimpleMailMessage` is sent via `JavaMailSender` with the correct to, from, subject, and body

### Includes display name in sender address

- **Given** both `open-elements.email.from` and `open-elements.email.from-name` are configured
- **When** `sendEmail("recipient@example.com", "Subject", "Body")` is called
- **Then** the from field is formatted as `"Display Name <address>"` (e.g. `"My App <noreply@example.com>"`)

### Uses plain address when no display name is configured

- **Given** `open-elements.email.from` is set but `open-elements.email.from-name` is not
- **When** `sendEmail("recipient@example.com", "Subject", "Body")` is called
- **Then** the from field is the plain address (e.g. `"noreply@example.com"`)

## Sending Email by User

### Sends email to user's stored address

- **Given** a `UserEntity` with email `"user@example.com"` and the service is configured
- **When** `sendEmail(user, "Subject", "Body")` is called
- **Then** the email is sent to `"user@example.com"` with the correct subject and body

### Throws when user has no email address

- **Given** a `UserEntity` with a `null` email
- **When** `sendEmail(user, "Subject", "Body")` is called
- **Then** an `IllegalArgumentException` is thrown
- **And** no email is sent

### Throws when user has a blank email address

- **Given** a `UserEntity` with an empty string as email
- **When** `sendEmail(user, "Subject", "Body")` is called
- **Then** an `IllegalArgumentException` is thrown
- **And** no email is sent

## Graceful Degradation

### Logs warning at startup when mail sender is not available

- **Given** no `spring.mail.host` is configured (so `JavaMailSender` is not auto-configured)
- **When** the application starts
- **Then** a WARNING is logged indicating that email sending is not available

### Throws at call time when mail sender is not available

- **Given** no `JavaMailSender` is available (SMTP not configured)
- **When** `sendEmail("recipient@example.com", "Subject", "Body")` is called
- **Then** an `EmailException` is thrown with a message indicating that mail sending is not configured

### Throws at call time when from address is not configured

- **Given** `JavaMailSender` is available but `open-elements.email.from` is not set
- **When** `sendEmail("recipient@example.com", "Subject", "Body")` is called
- **Then** an `EmailException` is thrown with a message indicating that the sender address is not configured

## Error Handling

### Wraps mail send failure as EmailException

- **Given** the service is configured and `JavaMailSender` throws a `MailException`
- **When** `sendEmail("recipient@example.com", "Subject", "Body")` is called
- **Then** an `EmailException` is thrown wrapping the original `MailException` as the cause

## Input Validation

### Rejects null recipient address

- **Given** the service is configured
- **When** `sendEmail(null, "Subject", "Body")` is called
- **Then** a `NullPointerException` is thrown

### Rejects null subject

- **Given** the service is configured
- **When** `sendEmail("recipient@example.com", null, "Body")` is called
- **Then** a `NullPointerException` is thrown

### Rejects null body

- **Given** the service is configured
- **When** `sendEmail("recipient@example.com", "Subject", null)` is called
- **Then** a `NullPointerException` is thrown

### Rejects null user

- **Given** the service is configured
- **When** `sendEmail((UserEntity) null, "Subject", "Body")` is called
- **Then** a `NullPointerException` is thrown
