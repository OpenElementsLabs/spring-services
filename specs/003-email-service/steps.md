# Implementation Steps: Email Sending Service

## Step 1: Add `spring-boot-starter-mail` dependency

- [x] Add the starter dependency to `pom.xml`

**Acceptance criteria:**
- [x] `./mvnw clean compile` succeeds
- [x] `JavaMailSender` is on the classpath

---

## Step 2: Create `EmailException`

- [x] Create `EmailException` extending `RuntimeException` with `(String)` and `(String, Throwable)` constructors

**Acceptance criteria:**
- [x] Project compiles

---

## Step 3: Create `EmailProperties`

- [x] `@ConfigurationProperties(prefix = "open-elements.email")` with `from` and `fromName` fields

**Acceptance criteria:**
- [x] Project compiles

---

## Step 4: Create `EmailConfig`

- [x] 4-annotation pattern + `@EnableConfigurationProperties(EmailProperties.class)`. No `@Bean` methods — `JavaMailSender` is auto-configured by Spring Boot

**Acceptance criteria:**
- [x] Project compiles

---

## Step 5: Create `EmailService`

- [x] `@Service` with constructor `(ObjectProvider<JavaMailSender>, EmailProperties)`
- [x] `@PostConstruct` warns when no `JavaMailSender` is available
- [x] `sendEmail(String to, String subject, String body)`:
  - validates inputs with `Objects.requireNonNull`
  - throws `EmailException` if mail sender or `from` not configured
  - builds `SimpleMailMessage` with formatted from address and dispatches
  - wraps `MailException` as `EmailException`
- [x] Overload `sendEmail(UserEntity user, String subject, String body)`:
  - throws `IllegalArgumentException` if user has null/blank email
  - delegates to the address-based overload

**Acceptance criteria:**
- [x] Project compiles
- [x] Unit tests pass

---

## Step 6: `package-info.java`

- [x] Concise Javadoc summarizing purpose, configuration, and scope

**Acceptance criteria:**
- [x] Project compiles

---

## Step 7: Register `EmailConfig` in `FullSpringServiceConfig`

- [x] Add `EmailConfig.class` to `@Import`
- [x] Update Javadoc bullet list

**Acceptance criteria:**
- [x] Project compiles

---

## Step 8: Tests

- [x] `EmailServiceTest` (Mockito) covering all behaviors
- [x] `EmailConfigTest` (`ApplicationContextRunner` + log capture) covering startup scenarios

**Acceptance criteria:**
- [x] `./mvnw test -Dtest='EmailServiceTest,EmailConfigTest'` is green (17/17)

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|---|---|---|
| Sends a plain-text email successfully | Backend | 8 |
| Includes display name in sender address | Backend | 8 |
| Uses plain address when no display name is configured | Backend | 8 |
| Sends email to user's stored address | Backend | 8 |
| Throws when user has no email address | Backend | 8 |
| Throws when user has a blank email address | Backend | 8 |
| Logs warning at startup when mail sender is not available | Backend | 8 (config test) |
| Throws at call time when mail sender is not available | Backend | 8 |
| Throws at call time when from address is not configured | Backend | 8 |
| Wraps mail send failure as EmailException | Backend | 8 |
| Rejects null recipient address | Backend | 8 |
| Rejects null subject | Backend | 8 |
| Rejects null body | Backend | 8 |
| Rejects null user | Backend | 8 |
