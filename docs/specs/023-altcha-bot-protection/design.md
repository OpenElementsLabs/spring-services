# Design: ALTCHA bot protection

## GitHub Issue

—

## Summary

A new optional feature module, `spring-services-altcha`, protects endpoints that accept requests
**without authentication** — a contact form, a sign-up form, a lead form on a public website — against
automated submissions. It uses [ALTCHA](https://altcha.org): the browser widget fetches a signed
challenge from the server, solves a small proof of work and sends the solution with the form; the
server verifies it. There is no external service, no cookie and no tracking, so the module needs no
consent banner and no data-processing agreement.

The module originates in the Support & Care Check server (Open Elements), where it protects the
public check form. It was written there as a self-contained package with a spring-services-style
auto-configuration so that it could move here unchanged.

## Goals

- One call for an application's endpoint: `AltchaService.requireVerified(payload)`.
- A challenge endpoint that the widget can reach without authentication, while everything else stays
  behind the default JWT chain.
- A uniform error response with a stable machine-readable code and a message for the visitor in the
  language of the request (English and German).
- Each solution is accepted once (replay protection), with a replaceable store for several instances.
- Opt-in: nothing changes for applications that do not enable the module, including consumers of
  `spring-services-all`.

## Non-goals

- Serving the widget script. The page loads `altcha` 3.x itself (npm or a CDN).
- Spam filtering of form content, rate limiting, IP blocking. These belong to the application.
- A shared (database/Redis) replay store. The interface is there; an implementation is left to the
  application until a second consumer needs one (see `docs/TODO.md`).
- ALTCHA protocol v1 (`maxNumber`, SHA-256 hash search). Only protocol v2 is supported.

## Technical approach

### Library

`org.altcha:altcha` 2.1.1 (MIT, one runtime dependency `org.json:json`), package
`org.altcha.altcha.v2`. Algorithm `PBKDF2/SHA-256`; difficulty is `cost × 16^keyPrefixLength`
PBKDF2 iterations on average (defaults 5000 and 2: well under a second in a current browser).

### Activation

Gated on `openelements.altcha.enabled=true`, like Search and DB-Backup, not on classpath presence like
Slack. The module cannot work without a signing secret, and self-activation would fail the start-up
of every `spring-services-all` consumer that has not configured one. When enabled without a secret of
at least 32 characters, start-up fails with a message that names the property.

### Packages

```
com.openelements.spring.base.altcha/
└── AltchaAutoConfiguration       # opt-in, all beans @ConditionalOnMissingBean
com.openelements.spring.base.services.altcha/
├── AltchaProperties              # openelements.altcha.*
├── AltchaService                 # requireVerified(payload), createChallenge()
├── AltchaVerificationException   # result(), code(), localized message
├── AltchaExceptionHandler        # 400 {"code", "message"}
├── AltchaChallengeController     # GET ${openelements.altcha.challenge-path}
├── AltchaVerifier                # verify(payload) -> VerificationResult (lower level)
├── ReplayStore                   # markUsed(signature, expiresAt)
└── InMemoryReplayStore           # default, single instance
```

The beans live on the auto-configuration itself (as in `StorageAutoConfiguration`), because
`@ConditionalOnMissingBean` is only reliable while auto-configurations are processed.

### Security

The default JWT chain has no matcher and demands authentication for everything. The module therefore
contributes its own `SecurityFilterChain` (`altchaSecurityFilterChain`, `@Order(0)`) whose matcher is
exactly the challenge path: `GET` is permitted, every other method is denied. It cannot open anything
else. The auto-configuration runs after `SpringServicesCoreAutoConfiguration` and
`SecurityAutoConfiguration` and registers the chain only when an `HttpSecurity` bean exists.

The application's own form endpoint is **not** opened by the module. The application permits it
(typically with its own chain), and calls `requireVerified` before it does anything else.

### Error response

`AltchaVerificationException` carries the `VerificationResult` and a stable code:
`altcha-missing`, `altcha-invalid`, `altcha-expired`, `altcha-reused`. The message comes from
`messages*.properties` next to the class, in the locale of `LocaleContextHolder` (the
`Accept-Language` of the request). Unknown languages get English, independent of the JVM default
locale (`ResourceBundle.Control.getNoFallbackControl`). `AltchaExceptionHandler` answers with `400`
and `{"code": …, "message": …}`; an application with another error format declares its own bean of
that type.

### Configuration

| Property | Default | |
| --- | --- | --- |
| `openelements.altcha.enabled` | `false` | opt-in |
| `openelements.altcha.hmac-key` | — | required when enabled, ≥ 32 characters |
| `openelements.altcha.expires` | `PT10M` | time to solve and submit |
| `openelements.altcha.cost` | `5000` | PBKDF2 iterations per attempt |
| `openelements.altcha.key-prefix-length` | `2` | hex characters of the required prefix |
| `openelements.altcha.challenge-path` | `/api/altcha/challenge` | endpoint for the widget |

## Alternatives considered

- **hCaptcha / reCAPTCHA / Turnstile.** External services that see the visitor's IP and set or read
  cookies; they need consent and a processing agreement under GDPR. Rejected for that reason.
- **Friendly Captcha.** Also proof of work, but the verification goes through their API (or a paid
  self-hosted licence).
- **Self-activation on the classpath.** Consistent with Slack/E-Mail, but breaks start-up for
  `spring-services-all` consumers without a secret. Rejected.
- **Leaving the permit rule to the application.** Every consumer would have to add the same chain,
  and forgetting it fails silently in the browser (the widget gets a `401`). Rejected.

## Open questions

- Should a JPA-backed `ReplayStore` live in the module (table in `oe_spring_services`)? Deferred
  until a consumer runs more than one instance.
