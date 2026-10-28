# Behaviors: ALTCHA bot protection

## Activation

### Inert by default

- **Given** `spring-services-altcha` is on the classpath and `openelements.altcha.enabled` is unset
- **When** the application starts
- **Then** it starts without an `openelements.altcha.hmac-key`
- **And** no `AltchaService`, challenge endpoint, exception handler or filter chain is registered

### Enabled with a secret

- **Given** `openelements.altcha.enabled=true` and a `hmac-key` of at least 32 characters
- **When** the application starts
- **Then** `AltchaService`, `AltchaVerifier`, an `InMemoryReplayStore` and, in a servlet web
  application, the challenge endpoint and `AltchaExceptionHandler` are registered

### Enabled without a secret

- **Given** `openelements.altcha.enabled=true` and no, a blank or a shorter `hmac-key`
- **When** the application starts
- **Then** start-up fails with a message that names `openelements.altcha.hmac-key`

### Application beans win

- **Given** the module is enabled and the application declares its own `ReplayStore`
- **When** the application starts
- **Then** the application's store is used and no `InMemoryReplayStore` is registered

### No web part outside a web application

- **Given** the module is enabled in a non-web application
- **When** the application starts
- **Then** `AltchaService` is registered, but no controller and no exception handler

## Challenge endpoint

### Public GET

- **Given** the module is enabled and the default chain demands authentication for everything
- **When** an anonymous client sends `GET /api/altcha/challenge`
- **Then** the response is `200` with a signed challenge as JSON and `Cache-Control: no-store`

### Nothing else is opened

- **Given** the same setup
- **When** an anonymous client sends `POST /api/altcha/challenge` or `GET` on any other path
- **Then** the request is rejected (`403`)

## Verification

### A solved challenge is accepted once

- **Given** a challenge from the endpoint, solved like the widget does
- **When** the application's endpoint calls `requireVerified(payload)` twice with it
- **Then** the first call returns normally
- **And** the second throws `AltchaVerificationException` with code `altcha-reused`

### Missing, invalid, foreign and expired payloads

- **When** `verify` is called with `null` or a blank string **Then** the result is `MISSING`
- **When** it is called with something that is no ALTCHA payload **Then** the result is `INVALID`
- **When** it is called with a solution of a challenge signed with another key **Then** `INVALID`
- **When** it is called with a solution of an expired challenge **Then** `EXPIRED`

### Replay store forgets expired challenges

- **Given** an `InMemoryReplayStore` with one expired and one valid entry
- **When** another challenge is marked as used
- **Then** the expired entry is removed and the valid one is still rejected a second time

## Error response

### Stable code and localized message

- **Given** the module is enabled in a web application
- **When** a form is posted without `altcha` and with `Accept-Language: de`
- **Then** the response is `400` with `code` `altcha-missing` and a German `message`
- **And** with `Accept-Language: en` the `message` is English

### Unknown languages get English

- **Given** the JVM default locale is German
- **When** the message for a failure is requested in French
- **Then** it is the English message

### Every failure has a message

- **For** every failed `VerificationResult` and both English and German
- **Then** a message exists (not the bare key) and ends with a full stop

### Verified results are no failure

- **When** an `AltchaVerificationException` is constructed with `VERIFIED`
- **Then** an `IllegalArgumentException` is thrown
