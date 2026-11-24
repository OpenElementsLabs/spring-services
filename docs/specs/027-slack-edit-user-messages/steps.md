# Implementation Steps: Editing Slack messages of users

All production code goes into `spring-services-slack`. New classes live in
`com.openelements.spring.base.services.slack.authorization`, except `SlackUserAuthorizationRequiredException` (next to
`SlackException`) and `SlackUserAuthorizationAutoConfiguration` (next to `SlackAutoConfiguration`). None of the classes
in `services.slack.authorization` carries a stereotype annotation, because `SlackConfig` component-scans
`services.slack` and its sub-packages.

Slack's Web API is never called for real in automated tests: the Slack SDK is pointed at a local WireMock server through
`com.slack.api.SlackConfig#setMethodsEndpointUrlPrefix`. The token store runs against Postgres via Testcontainers.

---

## Step 1: Test dependencies and properties

- [ ] Add `wiremock-standalone`, `spring-boot-testcontainers`, `testcontainers-postgresql` and
  `testcontainers-junit-jupiter` (test scope) to `spring-services-slack/pom.xml`, as in `spring-services-core`
- [ ] Create `SlackUserAuthorizationProperties` (`@ConfigurationProperties("open-elements.slack.user-authorization")`):
  `enabled` (default `false`), `clientId`, `clientSecret`, `publicBaseUrl`, `path` (default `/slack/oauth`),
  `tokenEncryptionKey`; JSpecify `@Nullable` on the optional values
- [ ] A method that returns the names of missing or invalid properties (blank counts as missing; key must be Base64 of
  32 bytes), used for the start-up warning and the `503`
- [ ] Unit tests for defaults, binding and the missing-property report

**Acceptance criteria:**
- [ ] `./mvnw -pl spring-services-slack -am verify` succeeds

**Related behaviors:** Enabled but not configured, Invalid encryption key

---

## Step 2: `SlackTokenCipher`

- [ ] AES-256-GCM, random 12-byte IV per encryption, output `IV || ciphertext+tag`
- [ ] `byte[] encrypt(String token, String teamId, String userId)` and
  `Optional<String> decrypt(byte[] data, String teamId, String userId)`; team and user id as associated data
- [ ] `decrypt` returns empty for a wrong key, a tampered ciphertext, other associated data or a too-short input — it
  never throws for bad data
- [ ] Unit tests: round trip, two encryptions differ, wrong key, flipped byte, other team, other user, empty input

**Related behaviors:** Tokens are encrypted at rest, Ciphertext is bound to its row, Wrong key

---

## Step 3: `SlackUserTokenStore` and `JdbcSlackUserTokenStore`

- [ ] Public interface `SlackUserTokenStore` with `find`, `save`, `delete` (Javadoc: implementations must store tokens
  encrypted or in a secret store)
- [ ] `JdbcSlackUserTokenStore(JdbcClient, SlackTokenCipher, Clock)`: parameterized statements only; `save` as
  `INSERT … ON CONFLICT (team_id, user_id) DO UPDATE`; `find` deletes the row and logs a warning (team and user id
  only) when decryption fails
- [ ] Migration SQL for `oe_spring_services.slack_user_tokens` as a test resource, applied by the integration test
  (the library ships no Flyway migrations; the same SQL goes into the upgrade guide in Step 8)
- [ ] Integration test against Postgres: round trip, separation per team and user, upsert, delete of a missing row,
  plain text not in the column, copied ciphertext, wrong key

**Related behaviors:** Token storage (all)

---

## Step 4: Slack client for user tokens

- [ ] Declare the SDK's `Slack` instance as a bean in `SlackConfig` (`@ConditionalOnMissingBean`), and build the
  existing bot `MethodsClient` from it, so tests can point both at WireMock
- [ ] Existing `SlackServiceTest` still passes unchanged

**Related behaviors:** — (foundation)

---

## Step 5: Editing in `SlackService`

- [ ] `SlackUserAuthorizationRequiredException extends SlackException` with `authorizationUrl()`
- [ ] `SlackService.updateUserMessage(SlackMessageReceivedEvent, String)`: feature off or not configured →
  `SlackException`; no token → authorization exception; `chat.update` with user token, channel, ts, text and empty
  `blocks`; `token_revoked` / `invalid_auth` / `account_inactive` / `token_expired` → delete and authorization
  exception; other errors and I/O → `SlackException`
- [ ] `isUserAuthorized(String, String)` and `userAuthorizationUrl()`
- [ ] Collaborators through `ObjectProvider`s, so `SlackService` keeps working without the feature
- [ ] Tests with WireMock for `chat.update` (request body asserted, including `blocks=[]` and the user token in the
  `Authorization` header) and an in-memory `SlackUserTokenStore` test implementation

**Related behaviors:** Editing (all), Edit without the feature, Bot token not required for editing

---

## Step 6: Authorization endpoints, security chain and auto-configuration

- [ ] `SlackUserAuthorizationProblemType` with the three slugs
- [ ] `SlackUserAuthorizationEndpoint` (`RouterFunction`): `/authorize` (state, cookie, redirect) and `/callback`
  (steps 1–7 of the design); problems through `ProblemFactory` when available, bare status otherwise
- [ ] `SlackUserAuthorizationAutoConfiguration`: `@ConditionalOnProperty(enabled)`, servlet web apps only; registers
  cipher, `JdbcSlackUserTokenStore` (`@ConditionalOnMissingBean(SlackUserTokenStore.class)`), endpoint and
  `slackUserAuthorizationSecurityFilterChain` (`@Order(0)`, only with `HttpSecurity`); start-up warning for missing
  properties; clear start-up failure without store and `DataSource`
- [ ] Register it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] Context-runner tests for activation; MockMvc tests with Spring Security for both endpoints, `oauth.v2.access`
  served by WireMock; a log-capture test that no secret appears

**Related behaviors:** Activation (all), Authorization endpoints (all), Security chain (all)

---

## Step 7: `tokens_revoked` and the no-loop guarantee

- [ ] `SlackEventsEndpoint` handles `event_callback` with event `tokens_revoked`: deletes `tokens.oauth` users of the
  envelope's `team_id` through the store if one exists; not published, `200`
- [ ] Test that a `message_changed` event publishes nothing (pins the existing subtype filter for this use case)

**Related behaviors:** No loop, Revocation through events

---

## Step 8: Documentation

- [ ] README: section "Editing users' Slack messages" with the listener example, properties and Slack app setup
- [ ] `package-info.java` of `services.slack.authorization`: what a user token allows and why it is stored encrypted
- [ ] `docs/releases/upgrade-to-2.1.md`: new section with the table migration SQL
- [ ] `docs/TODO.md`: key rotation, success redirect, editing the bot's own messages (`sendMessage` returning `ts`)

---

## Step 9: Verification against a real Slack workspace

Not automated; results recorded here before merge.

- [ ] Message with bold text, a mention and a link is edited; formatting and "(edited)" visible
- [ ] Message with an attached file keeps the file after the edit — **note:** spec 026 drops `file_share` messages, so
  this needs a message whose event arrives without subtype; record what Slack actually sends
- [ ] The edit produces `message_changed` and no listener call
- [ ] Revoking the app's authorization in Slack: next edit deletes the token and asks again; with `tokens_revoked`
  subscribed, the row is gone immediately
- [ ] Cancelling Slack's dialog shows the `400` page
