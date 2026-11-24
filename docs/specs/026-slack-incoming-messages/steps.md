# Implementation Steps: Incoming Slack messages

All production code goes into `spring-services-slack`. New classes live in
`com.openelements.spring.base.services.slack.events`, except `SlackEventsAutoConfiguration`, which sits next to
`SlackAutoConfiguration` in `com.openelements.spring.base.slack`. None of the classes in `services.slack.events` carries a
stereotype annotation, because `SlackConfig` component-scans `services.slack` and its sub-packages.

Tests use no mocks: time comes from a fixed or mutable `Clock`, requests are signed with the real verifier algorithm
by a test helper, listeners are real `@EventListener` beans, and HTTP runs through MockMvc with Spring Security.

---

## Step 1: Test dependencies and `SlackEventsProperties`

- [x] Add `spring-boot-starter-webmvc-test` and `spring-boot-starter-security-test` (test scope) to
  `spring-services-slack/pom.xml`, as in `spring-services-altcha`
- [x] Create `SlackEventsProperties` (`@ConfigurationProperties("open-elements.slack.events")`): `enabled` (default
  `false`), `signingSecret`, `path` (default `/slack/events`), `teamId`, `appId`; JSpecify `@Nullable` on the optional
  values
- [x] Unit test for defaults and binding

**Acceptance criteria:**
- [x] `./mvnw -pl spring-services-slack -am verify` succeeds
- [x] Property binding test passes

**Related behaviors:** — (foundation)

---

## Step 2: `SlackSignatureVerifier`

- [x] Create `SlackSignatureVerifier(String signingSecret, Clock clock)` (blank secret → `IllegalArgumentException`) with
  `boolean verify(@Nullable String timestamp, @Nullable String signature, byte[] rawBody)`
- [x] Reject missing headers and non-numeric timestamps; ±5 minutes tolerance, boundary inclusive
- [x] HMAC-SHA256 over `"v0:" + timestamp + ":"` followed by the raw bytes; compare `v0=<hex>` with
  `MessageDigest.isEqual`
- [x] Test helper `SlackRequestSigner` (test sources) that produces the headers for a body, secret and time
- [x] Unit tests: valid, wrong secret, tampered body (one byte changed), timestamp 5 min old (accepted), 5 min + 1 s old
  and 5 min + 1 s in the future (rejected), missing signature, missing timestamp, non-numeric timestamp, signature
  without `v0=` prefix
- [x] One test against the example request published by Slack (checked independently with `openssl`), so the helper and the
  verifier cannot share the same mistake

**Acceptance criteria:**
- [x] Project builds, all verifier tests pass

**Related behaviors:** Valid signature is accepted, Wrong secret, Tampered body, Stale or future timestamp, Timestamp
at the boundary, Missing or malformed headers (unit level; HTTP level in Step 8)

---

## Step 3: `SlackEventDeduplicator`

- [x] Create `SlackEventDeduplicator(Clock clock, Duration retention)` with `boolean firstSeen(String eventId)`
- [x] `ConcurrentHashMap<String, Instant>`; expired entries are removed on each call (as `InMemoryReplayStore` does)
- [x] Unit tests with a mutable test clock: first call `true`, second `false`, after `retention` again `true`, expired
  entries are removed, concurrent calls for one id yield exactly one `true`

**Acceptance criteria:**
- [x] Project builds, all deduplicator tests pass

**Related behaviors:** Retry is delivered once, Expired entries are forgotten (unit level; HTTP level in Step 8)

---

## Step 4: Event, problem types and exception

- [x] Create `SlackMessageKind` (`MESSAGE`, `APP_MENTION`)
- [x] Create `SlackMessageReceivedEvent` as a record (published as payload event) with the components from the design;
  compact constructor rejects `null` for required values; `channelType` and `threadTs` are `@Nullable`
- [x] Create `SlackEventsProblemType implements ProblemType` with `slack-events-not-configured` (503),
  `slack-event-too-large` (413), `slack-signature-invalid` (401), modelled on `AltchaProblemType`
- [x] Create `SlackEventsNotConfiguredException extends ProblemException`
- [x] Create package-private `SlackEventMapper`: turns a verified `event_callback` JSON (Jackson 3 `JsonNode`) into
  `Optional<SlackMessageReceivedEvent>` — empty for other event types, a `bot_id` or a `subtype`. Not listed in the
  design's package overview; it keeps the filter rules testable without HTTP
- [x] Unit tests for the mapper: channel message (all fields mapped, `kind` `MESSAGE`), `app_mention` (`kind`
  `APP_MENTION`, no channel type), `thread_ts` present / absent, `bot_id` → empty, subtypes `message_changed`,
  `message_deleted`, `channel_join` → empty, `reaction_added` → empty
- [x] Unit test for the problem types (slug, status), as `AltchaProblemTypeTest`

**Acceptance criteria:**
- [x] Project builds, mapper and problem type tests pass

**Related behaviors:** Channel message, Mention, Thread reply, Bot messages are dropped, Subtypes are dropped, Other
event types are ignored (unit level; HTTP level in Step 8)

---

## Step 5: `SlackEventsEndpoint`

- [x] Create `SlackEventsEndpoint` with a `ServerResponse handle(ServerRequest)` method implementing steps 1–8 of the
  design in that order
- [x] Not configured (verifier absent) → throw `SlackEventsNotConfiguredException`
- [x] Reject a `Content-Length` above 1 MiB up front; otherwise read at most 1 MiB + 1 byte from the input stream and
  reject if exceeded → `ProblemException(slack-event-too-large)`
- [x] Invalid signature, foreign `team_id` / `api_app_id` → `ProblemException(slack-signature-invalid)` without detail;
  log at `WARN` with the reason only
- [x] Parse JSON only after verification; `url_verification` → `200 text/plain` with the challenge; `event_callback` →
  de-duplicate, map, publish; anything else → `200`
- [x] Publish via `Executor.execute(() -> publisher.publishEvent(event))`; catch and log listener exceptions inside the
  task
- [x] No tests in this step — the endpoint is tested through HTTP in Step 8, where security and problem details are
  part of the picture

**Acceptance criteria:**
- [x] Project builds

**Related behaviors:** — (tested in Step 8)

---

## Step 6: `SlackEventsAutoConfiguration`

- [x] Create `SlackEventsAutoConfiguration` (`@AutoConfiguration(after = SlackAutoConfiguration, core, servlet web
  security, TaskExecutionAutoConfiguration)`, servlet web applications only,
  `@ConditionalOnProperty("open-elements.slack.events.enabled", havingValue = "true")`,
  `@EnableConfigurationProperties(SlackEventsProperties.class)`)
- [x] Beans, each `@ConditionalOnMissingBean`: `slackEventsClock` (named, so it does not compete with an application
  `Clock`), `SlackEventDeduplicator` (1 h), `slackEventsTaskExecutor` (`SimpleAsyncTaskExecutor` with virtual threads,
  thread name prefix `slack-events-`), `SlackEventsEndpoint`
- [x] Verifier (not a bean) only with a non-blank secret; otherwise log the warning naming
  `open-elements.slack.events.signing-secret` once at start-up
- [x] `RouterFunction` bean for `POST ${path}`
- [x] Endpoint answers rejections itself: problem via `ProblemFactory` when present, bare status otherwise (decision
  after Step 5)
- [x] `slackEventsSecurityFilterChain` (`@Order(0)`, `@ConditionalOnBean(HttpSecurity.class)`): matcher = path; `POST`
  permitted, everything else denied; CSRF disabled
- [x] Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Acceptance criteria:**
- [x] Project builds; existing Slack tests still pass

**Related behaviors:** — (tested in Steps 7 and 8)

---

## Step 7: Auto-configuration tests

- [x] `SlackEventsAutoConfigurationTest` with `WebApplicationContextRunner`, modelled on `AltchaAutoConfigurationTest`
- [x] Inert by default: no endpoint, chain, executor, deduplicator
- [x] Enabled with secret: all beans present, no warning in the captured log output (`OutputCaptureExtension`)
- [x] Enabled without / with blank secret: context starts, warning names the property
- [x] Enabled with secret, no `open-elements.slack.token`: context starts, endpoint present
- [x] Application declares `slackEventsTaskExecutor`: the application's bean is used
- [x] Spring Boot's `applicationTaskExecutor` stays in place next to the feature's executor
- [x] Nothing is registered outside a servlet web application

**Acceptance criteria:**
- [x] All tests pass

**Related behaviors:** Inert by default (beans), Enabled with a signing secret, Enabled without a signing secret,
Sending is independent of receiving, Application beans win

---

## Step 8: HTTP tests

- [x] `SlackEventsWebTest`, modelled on `AltchaWebTest`: security, WebMvc, Jackson, problem details and trace
  auto-configurations, plus a stand-in default chain that demands authentication for everything; fixed `Clock`; a
  recording `@EventListener`; a synchronous executor bean so publication can be asserted directly (Step 9 covers the
  real executor)
- [x] JSON fixtures for `url_verification`, `message`, `app_mention`, bot message, subtypes, `reaction_added` under
  `src/test/resources/slack-events/`, shaped after Slack's documented payloads
- [x] Tests, each asserting status, problem `type` where applicable, and recorded events:
  - [x] disabled: `POST /slack/events` → `401` from the default chain
  - [x] not configured: `url_verification` and message → `503` `slack-events-not-configured`, no event
  - [x] `GET /slack/events` and `POST /other` → rejected
  - [x] custom path: processed on `/integrations/slack`, `/slack/events` not opened
  - [x] valid signature → `200`; wrong secret, tampered body, stale and future timestamp, missing / malformed headers →
    `401` `slack-signature-invalid` without `detail`; timestamp exactly 5 min old → `200`
  - [x] invalid JSON without valid signature → `401`
  - [x] with `openelements.problem-details.enabled=false`: rejections keep their status (`401`, `413`, `503`) with an
    empty body
  - [x] 1 MiB + 1 byte body → `413` `slack-event-too-large`
  - [x] configured `team-id` / `app-id` with a mismatching payload → `401`, no event
  - [x] rejections: captured log has a `WARN` entry and contains neither the body, the signature header nor the secret
  - [x] `url_verification` with challenge `abc123` → `200`, body `abc123`, also with `team-id` and `app-id` configured
  - [x] signed body that is no JSON → `400`, no event
  - [x] message, mention, thread reply → expected event; bot message, subtypes, `reaction_added`, unknown `type` →
    `200`, no event
  - [x] same `event_id` twice (second with `X-Slack-Retry-Num: 1`) → one event; after advancing the clock by more than
    one hour → second event

**Acceptance criteria:**
- [x] All tests pass

**Related behaviors:** Inert by default (HTTP), Not configured rejects everything, Custom path, Only POST on the events
path is open, Valid signature is accepted, Wrong secret, Tampered body, Stale or future timestamp, Timestamp at the
boundary, Missing or malformed headers, Body is parsed only after verification, Oversized body, Foreign workspace or
app, Rejections do not leak data, Challenge is echoed, Signed body that is no JSON, Channel message, Mention, Thread reply, Bot messages are dropped,
Subtypes are dropped, Other event types are ignored, Retry is delivered once, Expired entries are forgotten

---

## Step 9: Asynchronous delivery tests

- [x] `SlackEventsAsyncWebTest` with the real `slackEventsTaskExecutor`
- [x] Slow listener: blocks on a `CountDownLatch`; the response is `200` while the latch is still closed; the test
  releases the latch and then awaits delivery with a bounded `await` (no sleeps)
- [x] Failing listener: throws; the response is `200`; the captured log contains the exception (awaited with
  Awaitility, bounded)
- [x] The listener runs on a `slack-events-` thread

**Acceptance criteria:**
- [x] All tests pass, deterministically (latches with timeouts, no `Thread.sleep`)

**Related behaviors:** Slow listener does not delay the response, Failing listener does not affect the response

---

## Step 10: Documentation

- [x] `package-info.java` for `services.slack.events`: usage with `@EventListener`, configuration table, Slack app
  setup (Request URL, `app_mention` / `message.im`, scopes), the warning that a verified message is still user input
  and the sender must be authorised by the application
- [x] Update `services/slack/package-info.java`: the "Scope" paragraph no longer says the module only sends
- [x] `README.md`: extend the Slack section with receiving messages and the new properties; update the module tree
  entry (line ~582)
- [x] `CLAUDE.md` has no Project Context section; nothing to update there unless one is added
- [x] `docs/TODO.md`: shared de-duplication store for several instances (from "Open questions"), and verifying the
  filter chain in a real application (same open question as the ALTCHA chain)
- [x] `docs/specs/INDEX.md`: status `done` once all steps are checked

**Acceptance criteria:**
- [x] `./mvnw verify` succeeds for the whole reactor; `javadoc:javadoc` for `spring-services-slack` without warnings
  (the Javadoc plugin is not bound to `verify`)

**Related behaviors:** —

---

## Step 11: Fixes from `/spec-review`

- [x] `slackEventsClock` and `slackEventsTaskExecutor` with `defaultCandidate = false`: an application that injects a
  `Clock` or an `Executor` by type failed to start with two candidates once the feature was enabled
- [x] Regression tests in `SlackEventsAutoConfigurationTest` for both cases
- [x] `SlackEventsProperties` treats a blank `team-id` / `app-id` as unset; property and HTTP test
- [x] `SlackEventsEndpointTest`: the size limit holds for a body without `Content-Length` (MockMvc always sends one)
- [x] Outdated statements corrected: verifier in `behaviors.md`, "same name" in the auto-configuration Javadoc, open
  question in `design.md`

**Acceptance criteria:**
- [x] All Slack tests pass

**Related behaviors:** Feature beans do not compete with application beans, Foreign workspace or app, Oversized body

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
| --- | --- | --- |
| Inert by default | Backend | 7, 8 |
| Enabled with a signing secret | Backend | 7 |
| Enabled without a signing secret | Backend | 7 |
| Not configured rejects everything | Backend | 8 |
| Sending is independent of receiving | Backend | 7 |
| Application beans win | Backend | 7 |
| Feature beans do not compete with application beans | Backend | 11 |
| Custom path | Backend | 8 |
| Only POST on the events path is open | Backend | 8 |
| Valid signature is accepted | Backend | 2, 8 |
| Wrong secret | Backend | 2, 8 |
| Tampered body | Backend | 2, 8 |
| Stale or future timestamp | Backend | 2, 8 |
| Timestamp at the boundary | Backend | 2, 8 |
| Missing or malformed headers | Backend | 2, 8 |
| Body is parsed only after verification | Backend | 8 |
| Oversized body | Backend | 8, 11 |
| Foreign workspace or app | Backend | 8, 11 |
| Rejections do not leak data | Backend | 8 |
| Challenge is echoed | Backend | 8 |
| Signed body that is no JSON | Backend | 8 |
| Channel message | Backend | 4, 8 |
| Mention | Backend | 4, 8 |
| Thread reply | Backend | 4, 8 |
| Bot messages are dropped | Backend | 4, 8 |
| Subtypes are dropped | Backend | 4, 8 |
| Other event types are ignored | Backend | 4, 8 |
| Retry is delivered once | Backend | 3, 8 |
| Expired entries are forgotten | Backend | 3, 8 |
| Slow listener does not delay the response | Backend | 9 |
| Failing listener does not affect the response | Backend | 9 |
