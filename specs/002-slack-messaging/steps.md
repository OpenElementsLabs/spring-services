# Implementation Steps: Slack Messaging Service

## Step 1: Add Slack SDK dependency

- [ ] Add `slack-api-client.version` property (1.45.3) to `pom.xml`
- [ ] Add `com.slack.api:slack-api-client` dependency (compile scope)

**Acceptance criteria:**
- [ ] `./mvnw clean compile` succeeds
- [ ] Dependency is resolvable

---

## Step 2: Create `SlackException`

- [ ] Create `src/main/java/com/openelements/spring/base/services/slack/SlackException.java`
- [ ] Extends `RuntimeException`
- [ ] Constructors: `(String message)` and `(String message, Throwable cause)`

**Acceptance criteria:**
- [ ] Project compiles

---

## Step 3: Create `SlackProperties`

- [ ] Create `SlackProperties.java` annotated with `@ConfigurationProperties(prefix = "open-elements.slack")`
- [ ] Single field: `String token`
- [ ] Standard getter/setter or record style

**Acceptance criteria:**
- [ ] Project compiles
- [ ] Property binding works (verified in tests)

---

## Step 4: Create `SlackConfig`

- [ ] Create `SlackConfig.java` with `@Configuration`, `@ComponentScan`, `@AutoConfiguration`, `@EnableAutoConfiguration`, `@EnableConfigurationProperties(SlackProperties.class)`
- [ ] Provide `MethodsClient` bean from configured token; bean is `null` (or marker) when token is missing/blank — done by exposing `@Bean(name = "slackMethodsClient")` returning `MethodsClient` or `null` via `@Nullable`
- [ ] Add `@PostConstruct` on the config (or service) that warns when token is missing

**Acceptance criteria:**
- [ ] Project compiles

---

## Step 5: Create `SlackService`

- [ ] Create `SlackService.java` annotated with `@Service`
- [ ] Constructor receives optional `MethodsClient` (nullable) injected from config
- [ ] `sendMessage(@NonNull String channel, @NonNull String text)` posts via `chat.postMessage`
- [ ] Throws `SlackException` if token missing, on `IOException`, on `SlackApiException`, or when API response is not OK
- [ ] Uses `Objects.requireNonNull` for parameter validation
- [ ] `@PostConstruct` logs warning when no client is available

**Acceptance criteria:**
- [ ] Project compiles
- [ ] Unit tests for the service pass

---

## Step 6: Add `package-info.java`

- [ ] Create `package-info.java` with concise Javadoc summarizing the package

**Acceptance criteria:**
- [ ] Project compiles

---

## Step 7: Register `SlackConfig` in `FullSpringServiceConfig`

- [ ] Add `SlackConfig.class` to the `@Import` array
- [ ] Update Javadoc bullet list

**Acceptance criteria:**
- [ ] Project compiles

---

## Step 8: Unit / integration tests

- [ ] `SlackServiceTest` — Mockito-based unit tests covering all behaviors:
  - [ ] Send message to channel by name (happy path)
  - [ ] Send message to channel by ID (happy path)
  - [ ] Send message containing a link
  - [ ] Send without configured token → SlackException
  - [ ] Send with blank token → SlackException
  - [ ] Send to invalid channel → SlackException with Slack error in message
  - [ ] Send with revoked token → SlackException
  - [ ] Network error during send → SlackException with IOException cause
  - [ ] Null channel → NullPointerException
  - [ ] Null text → NullPointerException
- [ ] `SlackConfigTest` (or boot context test) — verifies:
  - [ ] App starts without token (warning logged, bean still registered)
  - [ ] App starts with valid token (no warning)
  - [ ] App starts with blank token (warning logged)

**Acceptance criteria:**
- [ ] All tests pass
- [ ] `./mvnw test` is green

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
