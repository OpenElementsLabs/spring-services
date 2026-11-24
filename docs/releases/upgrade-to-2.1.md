# Upgrade prompt: spring-services 2.0.0 → 2.1.0

`spring-services` 2.1.0 brings the changes below. Every section stands on its own — apply only the
ones that affect you:

| Change | Spec | Nature |
| --- | --- | --- |
| **Webhooks are delivered asynchronously** on a dedicated executor | — | behaviour change; **source break** only if you construct `WebhookSender` yourself |
| No database migration | — | — |

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

## Prompt

You are working inside a Spring Boot service that depends on `spring-services` 2.0.0 and moves to
2.1.0.

### 1. Adapt code that constructs `WebhookSender`

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

### 2. Check code that relied on synchronous delivery

Search for code that reads `lastStatus` or `lastCalledAt` of a webhook right after a `save(...)` or
`delete(...)` of another entity — typically in tests. After such a call the webhook may not have been
called yet. Wait for the value instead, for example with Awaitility:

```java
await().atMost(Duration.ofSeconds(10))
    .untilAsserted(() ->
        assertThat(webhookDataService.findById(id).orElseThrow().lastStatus()).isEqualTo(200));
```

Code that reads the status after `ping(id)` needs no change.

### 3. Check listeners on `OnObjectUpdate<WebhookDto>`

Search for listeners on `OnObjectUpdate` that handle `WebhookDto`. A webhook delivery no longer
publishes such an event; only changes made through `WebhookDataService.save(...)` (for example
editing the URL or the `active` flag) still do. If a listener used the delivery events to track call
results, read `lastStatus` / `lastCalledAt` from `WebhookDataService` instead.

### 4. Verify

- The application starts; `webhookTaskExecutor` and — if you rely on it — `applicationTaskExecutor`
  are both present.
- Saving an entity with an unreachable webhook registered returns immediately instead of after the
  connect timeout.
- The webhook's `lastStatus` / `lastCalledAt` are updated shortly after a delivery.

### Guard rails / Don't do this

- **Do not declare your own bean named `webhookTaskExecutor`.** The library does not back off from
  it, and Spring Boot refuses a second bean definition with the same name at startup.
- **Do not add `@EnableAsync` to "make webhooks async".** Delivery no longer depends on it.
- **Webhooks are not retried and not persisted before delivery.** A call that is still running on
  the executor when the application shuts down can be lost. If a receiver must get every event, it
  has to reconcile on its own, for example by reading the current state through the REST API.
