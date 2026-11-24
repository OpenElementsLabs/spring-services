# Design: Incoming Slack messages

## GitHub Issue

—

## Summary

`spring-services-slack` can send messages (spec 002) but cannot react to them. This spec adds the receiving side:
an endpoint for the Slack [Events API](https://api.slack.com/apis/events-api) that verifies every request with the
app's signing secret and publishes each incoming message as a Spring application event. Applications react with a
plain `@EventListener` and never see HTTP, signatures or Slack's envelope.

The feature is opt-in. Without a signing secret the application still starts, but the endpoint accepts nothing.

## Goals

- An application reacts to a Slack message with `@EventListener void on(SlackMessageReceivedEvent event)`.
- No request is processed unless its Slack signature is valid and recent. There is no configuration in which the
  endpoint accepts unverified requests.
- Slack gets its acknowledgement in time (< 3 s) regardless of how long listeners take.
- Slack's retries never reach a listener twice.
- The bot's own messages never reach a listener, so a listener that answers via `SlackService.sendMessage` cannot
  loop.
- Nothing changes for applications that do not enable the feature, including consumers of `spring-services-all`.

## Non-goals

- Socket Mode. The event type is transport-neutral, so a second transport can publish the same event later.
- Interactivity (buttons, modals, slash commands) and event types other than messages.
- Authorising the sender. The signature proves that a request comes from Slack, not that the Slack user may trigger
  anything. The event carries `teamId` and `userId`; the application decides.
- Replying in a thread. `SlackService.sendMessage` has no `threadTs` yet; that is a separate, small change.
- A shared de-duplication store for several instances (see "Open questions").
- Messages from other bots, edits, deletions and other message subtypes.

## Technical approach

### Transport

HTTP Events API, not Socket Mode: no additional dependency, no long-lived connection, stateless across instances,
and it fits the existing pattern of a module-owned path with its own `SecurityFilterChain` (SCIM, ALTCHA). The cost is
a publicly reachable endpoint; for local development a tunnel is needed.

The signature check is implemented in the module (about 20 lines, `javax.crypto.Mac`) instead of pulling in
`slack-app-backend` for `SlackSignature.Verifier`. The security-critical code stays visible and is tested directly.

### Activation

Gated on `open-elements.slack.events.enabled=true`. Classpath presence is not enough: the feature opens a public
path, and that must be a deliberate decision of the application.

When enabled **without** a signing secret, the application starts, logs a warning at start-up that names
`open-elements.slack.events.signing-secret`, and the endpoint rejects every request with `503` (see "Error
responses"). This mirrors the sending side, where a missing token does not prevent start-up either, while still
guaranteeing that nothing unverified is processed.

### Request processing

For `POST ${open-elements.slack.events.path}` (default `/slack/events`), in this order:

1. **Configured?** No signing secret → `SlackEventsNotConfiguredException` → `503`.
2. **Size.** The body is read as raw bytes up to 1 MiB. A larger body is rejected with `413` without reading the
   rest.
3. **Signature.** Headers `X-Slack-Request-Timestamp` and `X-Slack-Signature` must be present. The timestamp must
   be within ±5 minutes of the injected `Clock` (replay protection). The expected signature is
   `v0=` + hex(HMAC-SHA256(secret, `"v0:" + timestamp + ":" + rawBody`)) and is compared with
   `MessageDigest.isEqual` (constant time). Any failure → `401`. The JSON is parsed only after this step, from the
   same bytes that were verified.
4. **Dispatch by `type`:**
   - `url_verification` → `200` with the `challenge` as plain text.
   - `event_callback` → step 5.
   - anything else → `200`, ignored.

   A body that carries a valid signature but is no valid JSON → `400`. Slack never sends one; the case exists only
   so that a parser error is not turned into a `500`.
5. **Workspace and app.** If `team-id` or `app-id` are configured, `team_id` / `api_app_id` of the `event_callback`
   must match. Mismatch → `401`. Not applied to `url_verification`, whose body carries neither field.
6. **Filter.** Only `message` and `app_mention` events are considered. Events with a `bot_id` or with a `subtype`
   (edits, deletions, joins, bot messages) are dropped.
7. **De-duplication.** An `event_id` seen within the last hour → `200`, ignored. Slack retries up to three times
   within minutes; an hour is a generous margin. Applied after the filter, so dropped events take no space.
8. **Acknowledge and hand off.** The response is `200` with an empty body. The `SlackMessageReceivedEvent` is
   published on a dedicated `TaskExecutor`, so listener run time and listener exceptions never affect the response.
   A listener exception is logged. Whether the remaining listeners still run is Spring's multicaster behaviour
   (by default they do not); the module does not change that global setting.

Rejections are logged at `WARN` with the reason, never with the body or headers.

### The event

```java
public record SlackMessageReceivedEvent(
    SlackMessageKind kind,          // MESSAGE or APP_MENTION
    String eventId,
    String teamId,
    String channelId,
    @Nullable String channelType,   // "channel", "group", "im", "mpim"; absent for app_mention
    String userId,
    String text,
    String ts,
    @Nullable String threadTs,
    Instant eventTime) {}
```

A record published with `ApplicationEventPublisher.publishEvent(Object)`, not a subclass of `ApplicationEvent`:
Spring wraps it in a `PayloadApplicationEvent`, and `@EventListener void on(SlackMessageReceivedEvent event)` works
unchanged. Optional values are `@Nullable` (JSpecify), as record components should not be `Optional`.

If an app subscribes to both `app_mention` and `message.channels`, Slack delivers a mention in a channel twice, as two
different events. The library forwards both; `kind` lets a listener pick one.

### Packages

```
com.openelements.spring.base.slack/
├── SlackAutoConfiguration           # existing
└── SlackEventsAutoConfiguration     # new, @ConditionalOnProperty(enabled), servlet web apps only
com.openelements.spring.base.services.slack.events/
├── SlackEventsProperties            # open-elements.slack.events.*
├── SlackSignatureVerifier           # verify(timestamp, signature, rawBody)
├── SlackEventsEndpoint              # functional endpoint (RouterFunction), steps 1–8
├── SlackEventDeduplicator           # in-memory event_id cache with expiry
├── SlackEventMapper                 # package-private: envelope → event, filter rules of step 6
├── SlackMessageReceivedEvent
├── SlackMessageKind
├── SlackEventsProblemType           # problem slugs of this feature
└── SlackEventsNotConfiguredException
```

`SlackConfig` scans `com.openelements.spring.base.services.slack` including sub-packages. None of the new classes
carries a stereotype annotation; all beans are declared on `SlackEventsAutoConfiguration`, so the property gate cannot
be bypassed by that scan. For the same reason the endpoint is a `RouterFunction` bean rather than an
`@RestController`.

The auto-configuration is limited to servlet web applications; without one there is nothing to receive requests.
Every bean is `@ConditionalOnMissingBean`. The clock (`slackEventsClock`) and the executor (`slackEventsTaskExecutor`,
virtual threads) are named beans that an application replaces by declaring a bean of the same name. Both are declared
with `defaultCandidate = false` and injected by qualifier only, so an application that injects a `Clock` or an
`Executor` by type still gets its own bean instead of failing with two candidates. The auto-configuration runs after `TaskExecutionAutoConfiguration`, which backs
off as soon as any `Executor` bean exists, so the feature's executor never replaces the application's task executor.
The signature verifier is not a bean: the endpoint gets one only when a signing secret is set.

### Security

`slackEventsSecurityFilterChain` (`@Order(0)`), registered only when an `HttpSecurity` bean exists. Its matcher is
exactly the events path: `POST` is permitted without authentication, every other method is denied, CSRF is disabled.
Authentication is the signature check in the endpoint. The chain cannot open any other path.

Additional safeguards:

- The signing secret comes from the environment or secret management only and is never logged.
- No IP allow-listing: Slack does not publish stable source ranges; the signature is the intended mechanism.
- A verified message is still user input. Any member of the workspace (depending on settings, guests too) can write
  to the bot. Listeners that trigger actions must authorise `userId` themselves; the package documentation says so
  explicitly.

### Error responses

Slugs are defined in `SlackEventsProblemType` like `AltchaProblemType`. The endpoint answers rejections itself
instead of relying on the global exception handling: with problem details enabled (spec 024) it builds the RFC 9457
problem through the `ProblemFactory`; with `openelements.problem-details.enabled=false` there is no factory and no
handler for `ProblemException`, so it answers with the bare status and an empty body. The status is right in both
cases.

| Situation | Status | Slug |
| --- | --- | --- |
| No signing secret configured | `503` | `slack-events-not-configured` |
| Body larger than 1 MiB | `413` | `slack-event-too-large` |
| Missing/invalid signature, stale timestamp, foreign team or app | `401` | `slack-signature-invalid` |

The `401` deliberately has one slug and no detail, so a caller cannot tell which check failed.

### Configuration

| Property | Default | |
| --- | --- | --- |
| `open-elements.slack.events.enabled` | `false` | opt-in |
| `open-elements.slack.events.signing-secret` | — | without it every request gets `503` |
| `open-elements.slack.events.path` | `/slack/events` | Request URL configured in the Slack app |
| `open-elements.slack.events.team-id` | — | optional, restricts to one workspace; blank counts as unset |
| `open-elements.slack.events.app-id` | — | optional, restricts to one Slack app; blank counts as unset |

The prefix follows the existing `open-elements.slack.token`, not the `openelements.*` prefix of newer modules, so
that one feature has one prefix.

### Slack app setup (documented, not code)

Request URL `https://<host>/slack/events`; bot events `app_mention` (scope `app_mentions:read`) and, for direct
messages, `message.im` (scope `im:history`). `message.channels` is possible but delivers every message of every
channel the bot is in.

## Alternatives considered

- **Socket Mode.** No public endpoint, but an extra WebSocket dependency, a long-lived connection with reconnect
  logic and less obvious behaviour across instances. Deferred, not rejected.
- **Bolt for Java.** Its own app and routing model overlaps with Spring and adds a framework for a single endpoint.
- **`slack-app-backend` for signature verification.** An extra dependency for a few lines of code that we want under
  our own tests.
- **Fail start-up without a signing secret** (as ALTCHA does). Rejected: an optional integration should not take the
  whole application down, and the sending side does not either. Rejecting every request gives the same guarantee —
  nothing unverified is processed.
- **Synchronous listeners.** Simpler, but a slow listener would exceed Slack's 3-second limit and trigger retries.

## Open questions

- A shared de-duplication store (database) for applications with several instances. Until then each instance
  de-duplicates on its own; a retry that lands on another instance is delivered again. Deferred until a consumer runs
  more than one instance; recorded in `docs/TODO.md`.
