# Upgrade prompt: spring-services 2.0.0 → 2.1.0

`spring-services` 2.1.0 brings the changes below. Every section stands on its own — apply only the
ones that affect you:

| Change | Spec | Nature |
| --- | --- | --- |
| **Webhooks are delivered asynchronously** on a dedicated executor | — | behaviour change; **source break** only if you construct `WebhookSender` yourself |
| Optional **editing of users' Slack messages** | 027 | new optional feature, off by default, one new table |

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

---

## Asynchronous webhook delivery

Up to 2.0.0, `WebhookSender` was documented as asynchronous, but its `@Async` never took effect: the
annotated method was called through `this`, bypassing the Spring proxy, and the library never enabled
`@Async`. Every webhook call therefore ran sequentially on the thread that saved the data. With a
10 s connect and a 10 s read timeout per webhook, a slow or unreachable receiver blocked `save(...)`
and `delete(...)` of **every** data service — and with it the HTTP request that triggered them.

2.1.0 fixes this:

- Data events (`OnObjectCreate` / `OnObjectUpdate` / `OnObjectDelete`) are still dispatched after the
  originating transaction commits, but each webhook call now runs on the new executor bean
  `webhookTaskExecutor` (`WebhookConfig.EXECUTOR_BEAN_NAME`), one virtual thread per call.
  `save(...)` and `delete(...)` no longer wait for webhook receivers.
- `WebhookSender.ping(id)` still runs on the calling thread, so the recorded status is available as
  soon as it returns.
- The result of a call (`lastStatus`, `lastCalledAt`) is written through the new
  `WebhookDataService.recordCall(id, status, calledAt)`. It changes only these two columns and
  publishes **no** `OnObjectUpdate<WebhookDto>`. Before, a delivery saved the whole `WebhookDto`,
  which could overwrite a concurrent change of `url` or `active` with a stale value.
- `webhookTaskExecutor` is registered with `defaultCandidate = false`. It is never injected into an
  `Executor` injection point of your application, and Spring Boot's `applicationTaskExecutor` stays in
  place.

> **No wire change.** Receivers get the same payload as with 2.0.0 — only sooner from the point of
> view of the caller, and possibly in a different order when several webhooks are registered.

### Prompt

You are working inside a Spring Boot service that depends on `spring-services` 2.0.0 and moves to
2.1.0.

#### 1. Adapt code that constructs `WebhookSender`

Search the codebase for `new WebhookSender(` and for subclasses of `WebhookSender`. Most
applications have neither — the library wires the bean itself — and can skip this step.

The public constructor takes a third argument, the executor that delivers data events:

```java
// 2.0.0
new WebhookSender(restClient, webhookDataService);

// 2.1.0
new WebhookSender(restClient, webhookDataService, executor);
```

In a Spring context, inject the library's executor by name:

```java
@Qualifier(WebhookConfig.EXECUTOR_BEAN_NAME) Executor executor
```

In a plain unit test, `Runnable::run` delivers on the calling thread, which keeps the test
synchronous.

The former `protected void sendAndTrack(WebhookDto, WebhookEventPayload)` is now private. A subclass
that overrode it to change delivery must be rewritten; the public `sendAndTrack(WebhookDataEventPayload)`
and `ping(UUID)` are unchanged.

#### 2. Check code that relied on synchronous delivery

Search for code that reads `lastStatus` or `lastCalledAt` of a webhook right after a `save(...)` or
`delete(...)` of another entity — typically in tests. After such a call the webhook may not have been
called yet. Wait for the value instead, for example with Awaitility:

```java
await().atMost(Duration.ofSeconds(10))
    .untilAsserted(() ->
        assertThat(webhookDataService.findById(id).orElseThrow().lastStatus()).isEqualTo(200));
```

Code that reads the status after `ping(id)` needs no change.

#### 3. Check listeners on `OnObjectUpdate<WebhookDto>`

Search for listeners on `OnObjectUpdate` that handle `WebhookDto`. A webhook delivery no longer
publishes such an event; only changes made through `WebhookDataService.save(...)` (for example
editing the URL or the `active` flag) still do. If a listener used the delivery events to track call
results, read `lastStatus` / `lastCalledAt` from `WebhookDataService` instead.

#### 4. Verify

- The application starts; `webhookTaskExecutor` and — if you rely on it — `applicationTaskExecutor`
  are both present.
- Saving an entity with an unreachable webhook registered returns immediately instead of after the
  connect timeout.
- The webhook's `lastStatus` / `lastCalledAt` are updated shortly after a delivery.

#### Guard rails / Don't do this

- **Do not declare your own bean named `webhookTaskExecutor`.** The library does not back off from
  it, and Spring Boot refuses a second bean definition with the same name at startup.
- **Do not add `@EnableAsync` to "make webhooks async".** Delivery no longer depends on it.
- **Webhooks are not retried and not persisted before delivery.** A call that is still running on
  the executor when the application shuts down can be lost. If a receiver must get every event, it
  has to reconcile on its own, for example by reading the current state through the REST API.

---

## Editing users' Slack messages (spec 027)

`spring-services-slack` can now replace the text of a message a user wrote, in that user's name, so
that Slack shows it as edited — for example to add a translation. Slack lets only the author edit a
message, so each user authorizes the application once; the module stores that user's token encrypted.

The feature is **off by default**: unless `open-elements.slack.user-authorization.enabled=true` is
set, nothing is registered and there is nothing to migrate. This section applies only to consumers who
want to turn it on.

> **Source change for code that constructs `SlackService` itself.** Its constructor takes three new
> arguments (the Slack SDK and two `ObjectProvider`s for this feature). Applications that let Spring
> create the bean are not affected.
>
> **Source change for code that constructs `SlackEventsEndpoint` itself** (new in 2.1, spec 026). Its
> constructor takes an `ObjectProvider<SlackUserTokenStore>` as last argument.

### Prompt

You are working inside a Spring Boot service that depends on `spring-services-slack` (directly or via
`spring-services-all`) and wants listeners to edit the messages users write.

#### 1. Apply the database migration

Skip this step if you declare your own `SlackUserTokenStore` bean. Otherwise add a new versioned
migration to your own Flyway/Liquibase timeline:

```sql
-- Vx__slack_user_tokens.sql
CREATE TABLE oe_spring_services.slack_user_tokens (
    team_id         VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    encrypted_token BYTEA        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (team_id, user_id)
);
```

#### 2. Set up the Slack app

In the Slack app's settings, under *OAuth & Permissions*:

- Add the redirect URL `https://<host>/slack/oauth/callback`.
- Add the **user** token scope `chat:write` (not a bot scope).
- Keep *Token Rotation* off. The callback refuses tokens from an app with rotation on.

Optionally, if incoming messages (spec 026) are enabled, subscribe to the bot event `tokens_revoked`,
so a token is removed as soon as its user withdraws the permission.

#### 3. Configure the feature

Generate the encryption key once, for example with `openssl rand -base64 32`, and supply it and the
client secret from secret management:

```properties
open-elements.slack.user-authorization.enabled=true
open-elements.slack.user-authorization.client-id=1234.5678
open-elements.slack.user-authorization.client-secret=${SLACK_CLIENT_SECRET}
open-elements.slack.user-authorization.public-base-url=https://app.example.com
open-elements.slack.user-authorization.token-encryption-key=${SLACK_TOKEN_ENCRYPTION_KEY}
```

`public-base-url` is the URL under which browsers reach the application; the redirect URL from step 2
is derived from it. `open-elements.slack.events.team-id`, if set, also restricts authorizations to that
workspace.

#### 4. Edit messages in a listener

```java
@EventListener
void onSlackMessage(SlackMessageReceivedEvent message) {
    final String text = message.text() + "\n\n:gb: " + translator.toEnglish(message.text());
    try {
        slackService.updateUserMessage(message, text);
    } catch (SlackUserAuthorizationRequiredException e) {
        slackService.sendMessage(message.userId(), "Allow me to add translations: " + e.authorizationUrl());
    }
}
```

The new text replaces the message completely; pass the original text plus the addition to extend it.
Decide how often to ask a user who has not authorized — the example asks on every message.

#### 5. Verify

- The application starts without the warning "Editing Slack messages of users is enabled, but not
  configured".
- Opening `https://<host>/slack/oauth/authorize` leads to Slack's dialog; confirming it ends on
  "Slack authorization complete".
- The next message of that user is shown with the new text and "(edited)".

#### Guard rails / Don't do this

- **Never commit the client secret or the encryption key.** Anyone with a database dump and the key can
  act as every authorized user.
- **Changing the key makes every stored token unreadable.** The tokens are removed on use and the users
  have to authorize again.
- **Do not request more user scopes than `chat:write`.** The module needs no other, and every scope
  widens what a leaked token allows.
- **A verified message is still user input** (spec 026): authorise `userId()` before acting on it.
