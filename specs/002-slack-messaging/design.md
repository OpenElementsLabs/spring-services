# Design: Slack Messaging Service

## GitHub Issue

[#2 — Add service to send messages to Slack](https://github.com/OpenElementsLabs/spring-services/issues/2)

## Summary

Add a reusable Spring service that sends plain-text messages (including links) to Slack channels via a bot user. The
service is a library component — consuming applications inject `SlackService` and call
`sendMessage(String channel, String text)`. The service handles only the sending; triggering logic is the responsibility
of the consuming application.

## Goals

- Provide a simple, single-method API for sending Slack messages
- Support both channel names (`#general`) and channel IDs (`C01234ABCDE`)
- Integrate cleanly into the existing feature-config pattern of the project
- Fail gracefully at startup when no token is configured (warning log, not crash)

## Non-goals

- Rich formatting (Slack Block Kit) — plain text with links only
- Message threading — always top-level messages
- Retry logic — single attempt, caller handles retries if needed
- Persistence / audit log — fire-and-forget
- Multi-tenancy — single app-wide token, tenant-independent
- Triggering logic — the library does not decide when to send messages

## Technical Approach

### Dependency

Use `com.slack.api:slack-api-client` (latest stable, currently 1.45.x) to interact with the Slack Web API. Add the
dependency to `pom.xml` with a version property following the project convention for non-BOM dependencies.

### Package Structure

All classes live in `com.openelements.spring.base.services.slack`:

```
services/slack/
├── SlackConfig.java          — @Configuration, follows 4-annotation pattern
├── SlackProperties.java      — @ConfigurationProperties(prefix = "open-elements.slack")
├── SlackService.java          — @Service, public API
├── SlackException.java        — RuntimeException for all error cases
└── package-info.java          — Package-level Javadoc
```

### Configuration

`SlackProperties` binds the property `open-elements.slack.token` (a bot token like `xoxb-...`). This is the project's
first `@ConfigurationProperties` class. The config class uses `@EnableConfigurationProperties(SlackProperties.class)` to
register it — this is the canonical Spring Boot library pattern (no `@Component` on the properties class).

**Rationale:** Using `open-elements.slack` as prefix keeps all project configuration under one root namespace,
consistent with the `com.open-elements` groupId and avoids collisions with third-party Slack auto-configurations.

### Service Design

`SlackService` is a `@Service` with a single public method:

```java
public void sendMessage(@NonNull String channel, @NonNull String text)
```

- Accepts channel name or channel ID — the Slack `chat.postMessage` API handles both natively
- Messages are always sent as the configured bot user, always as top-level messages (no thread)
- Throws `SlackException` (unchecked) on any failure

**Rationale:** No separate interface is defined because no other service in this project uses interfaces — the concrete
class is the API surface, following the KISS principle.

### Testability

To avoid calling `Slack.getInstance()` inline (which is hard to mock), `SlackConfig` creates a `MethodsClient` bean
from the configured token. `SlackService` receives the `MethodsClient` via constructor injection (nullable — null when
token is missing). This makes the service fully unit-testable with Mockito.

**Rationale:** Option A (injectable `MethodsClient`) was chosen over Option B (`MockedStatic<Slack>`) because it keeps
tests clean and decoupled from implementation details.

### Error Handling

| Scenario | Behavior |
|---|---|
| Token not configured | `@PostConstruct` logs WARNING; `sendMessage` throws `SlackException` |
| Slack API returns error (e.g., invalid channel) | `SlackException` with error message from Slack |
| Network/IO error | `SlackException` wrapping the original `IOException` |
| `SlackApiException` from SDK | `SlackException` wrapping the original exception |

No retry logic. Single attempt per call.

**Rationale:** `@ConditionalOnProperty` was not used because it would prevent bean registration when the token is
absent, breaking any code that injects `SlackService`. The current design always registers the bean and fails at call
time.

### Integration

- `SlackConfig` is added to `FullSpringServiceConfig`'s `@Import` list
- Consuming apps can also import `SlackConfig` individually

## Dependencies

- `com.slack.api:slack-api-client` — Slack Web API client library
- Spring Boot auto-configuration (already present)

## Security Considerations

- The Slack bot token is a secret and must be provided via environment variables or secret management — never
  hard-coded or committed to version control
- No Slack-injection concerns — message content is not user-generated in typical usage

## Open Questions

None — all questions resolved in grill session.
