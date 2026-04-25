# Implementation Steps: Slack Messaging Service

## Step 1: Add Slack SDK dependency

- [x] Add `slack-api-client.version` property (1.45.3) to `pom.xml`
- [x] Add `com.slack.api:slack-api-client` dependency (compile scope)

**Acceptance criteria:**
- [x] `./mvnw clean compile` succeeds
- [x] Dependency is resolvable

---

## Step 2: Create `SlackException`

- [x] Create `src/main/java/com/openelements/spring/base/services/slack/SlackException.java`
- [x] Extends `RuntimeException`
- [x] Constructors: `(String message)` and `(String message, Throwable cause)`

**Acceptance criteria:**
- [x] Project compiles

---

## Step 3: Create `SlackProperties`

- [x] Create `SlackProperties.java` annotated with `@ConfigurationProperties(prefix = "open-elements.slack")`
- [x] Single field: `String token`
- [x] Standard getter/setter or record style

**Acceptance criteria:**
- [x] Project compiles
- [x] Property binding works (verified in tests)

---

## Step 4: Create `SlackConfig`

- [x] Create `SlackConfig.java` with `@Configuration`, `@ComponentScan`, `@AutoConfiguration`, `@EnableAutoConfiguration`, `@EnableConfigurationProperties(SlackProperties.class)`
- [x] Provide `MethodsClient` bean only when token is configured; absent otherwise so `ObjectProvider` returns no instance
- [x] `@PostConstruct` warning lives on `SlackService` (logically equivalent — both fire at app startup)

**Acceptance criteria:**
- [x] Project compiles

---

## Step 5: Create `SlackService`

- [x] Create `SlackService.java` annotated with `@Service`
- [x] Constructor receives `ObjectProvider<MethodsClient>` (Spring-idiomatic for optional bean) and resolves to a possibly-null `MethodsClient`
- [x] `sendMessage(@NonNull String channel, @NonNull String text)` posts via `chat.postMessage`
- [x] Throws `SlackException` if token missing, on `IOException`, on `SlackApiException`, or when API response is not OK
- [x] Uses `Objects.requireNonNull` for parameter validation
- [x] `@PostConstruct` logs warning when no client is available

**Acceptance criteria:**
- [x] Project compiles
- [x] Unit tests for the service pass

---

## Step 6: Add `package-info.java`

- [x] Create `package-info.java` with concise Javadoc summarizing the package

**Acceptance criteria:**
- [x] Project compiles

---

## Step 7: Register `SlackConfig` in `FullSpringServiceConfig`

- [x] Add `SlackConfig.class` to the `@Import` array
- [x] Update Javadoc bullet list

**Acceptance criteria:**
- [x] Project compiles

---

## Step 8: Unit / integration tests

- [x] `SlackServiceTest` — Mockito-based unit tests covering all behaviors:
  - [x] Send message to channel by name (happy path)
  - [x] Send message to channel by ID (happy path)
  - [x] Send message containing a link
  - [x] Send without configured token → SlackException
  - [x] Send with blank token → SlackException (covered by token-not-configured path: blank tokens skip bean creation in `SlackConfig`, so `SlackService` sees no client — same code path)
  - [x] Send to invalid channel → SlackException with Slack error in message
  - [x] Send with revoked token → SlackException
  - [x] Network error during send → SlackException with IOException cause
  - [x] Null channel → NullPointerException
  - [x] Null text → NullPointerException
- [x] `SlackConfigTest` — `ApplicationContextRunner`-based context tests verifying:
  - [x] App starts without token (warning logged, `SlackService` bean registered, no `MethodsClient` bean)
  - [x] App starts with valid token (no warning, both beans registered)
  - [x] App starts with blank token (warning logged, no `MethodsClient` bean)

**Acceptance criteria:**
- [x] All tests pass
- [x] `./mvnw test -Dtest='SlackServiceTest,SlackConfigTest'` is green (12/12)

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|---|---|---|
| Application starts without Slack token | Backend | 8 (config test) |
| Application starts with Slack token | Backend | 8 (config test) |
| Application starts with blank Slack token | Backend | 8 (config test) |
| Send message to channel by name | Backend | 8 |
| Send message to channel by ID | Backend | 8 |
| Send message containing a link | Backend | 8 |
| Send message without configured token | Backend | 8 |
| Send message with blank token | Backend | 8 |
| Send message to invalid channel | Backend | 8 |
| Send message with revoked token | Backend | 8 |
| Network error during send | Backend | 8 |
| Null channel parameter | Backend | 8 |
| Null text parameter | Backend | 8 |
