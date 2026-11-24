# Behaviors: Incoming Slack messages

## Activation

### Inert by default

- **Given** `spring-services-slack` is on the classpath and `open-elements.slack.events.enabled` is unset
- **When** the application starts
- **Then** no events endpoint, filter chain, executor or de-duplicator is registered
- **And** `POST /slack/events` is handled by the default chain (`401` without a JWT)

### Enabled with a signing secret

- **Given** `open-elements.slack.events.enabled=true` and a signing secret
- **When** the application starts
- **Then** the endpoint, `slackEventsSecurityFilterChain`, the de-duplicator and the executor are registered
- **And** no warning about the signing secret is logged

### Enabled without a signing secret

- **Given** `open-elements.slack.events.enabled=true` and no or a blank signing secret
- **When** the application starts
- **Then** start-up succeeds
- **And** a warning naming `open-elements.slack.events.signing-secret` is logged

### Not configured rejects everything

- **Given** the feature is enabled without a signing secret
- **When** any `POST /slack/events` arrives, including a `url_verification` request
- **Then** the response is `503` with problem type `slack-events-not-configured`
- **And** no event is published

### Sending is independent of receiving

- **Given** the feature is enabled with a signing secret but `open-elements.slack.token` is unset
- **When** the application starts
- **Then** start-up succeeds and the endpoint accepts verified requests

### Application beans win

- **Given** the feature is enabled and the application declares its own executor bean for Slack events
- **When** the application starts
- **Then** the application's executor is used

### Feature beans do not compete with application beans

- **Given** the feature is enabled and the application injects an `Executor` by type, or declares its own `Clock`
  and injects a `Clock` by type
- **When** the application starts
- **Then** start-up succeeds and the application gets its own bean (or Spring Boot's `applicationTaskExecutor`), not
  the feature's clock or executor

### Custom path

- **Given** `open-elements.slack.events.path=/integrations/slack`
- **When** a verified request is posted to `/integrations/slack`
- **Then** it is processed
- **And** `/slack/events` is not opened

## Security chain

### Only POST on the events path is open

- **Given** the feature is enabled and the default chain demands authentication for everything
- **When** an anonymous client sends `GET /slack/events` or `POST` to any other path
- **Then** the request is rejected

## Signature verification

### Valid signature is accepted

- **Given** a body signed with the configured secret and a current timestamp
- **When** it is posted
- **Then** the request is processed

### Wrong secret

- **Given** a body signed with another secret
- **When** it is posted
- **Then** the response is `401` with problem type `slack-signature-invalid` and no `detail`

### Tampered body

- **Given** a validly signed body that is changed by one character after signing
- **When** it is posted
- **Then** the response is `401`

### Stale or future timestamp

- **Given** a validly signed request whose timestamp is more than 5 minutes before or after the clock
- **When** it is posted
- **Then** the response is `401`

### Timestamp at the boundary

- **Given** a validly signed request whose timestamp is exactly 5 minutes old
- **When** it is posted
- **Then** the request is processed

### Missing or malformed headers

- **When** `X-Slack-Signature` or `X-Slack-Request-Timestamp` is missing, or the timestamp is not a number
- **Then** the response is `401`

### Body is parsed only after verification

- **Given** a body that is not valid JSON and carries no valid signature
- **When** it is posted
- **Then** the response is `401`, not a parse error

### Oversized body

- **Given** a body larger than 1 MiB
- **When** it is posted, with or without `Content-Length`
- **Then** the response is `413` with problem type `slack-event-too-large`

### Foreign workspace or app

- **Given** `team-id` is configured as `T1` and a validly signed event carries `team_id` `T2`
- **When** it is posted
- **Then** the response is `401` and no event is published
- **And** the same holds for a configured `app-id` and a different `api_app_id`
- **And** a blank `team-id` or `app-id` counts as unset, so events are accepted

### Rejections do not leak data

- **When** a request is rejected
- **Then** the log entry is at `WARN` and contains neither the body, the headers nor the signing secret

## URL verification

### Challenge is echoed

- **Given** a validly signed `url_verification` request with `challenge` `abc123`
- **When** it is posted
- **Then** the response is `200` with body `abc123`
- **And** the same holds when `team-id` and `app-id` are configured, although the request carries neither

### Signed body that is no JSON

- **Given** a body that is no valid JSON but carries a valid signature
- **When** it is posted
- **Then** the response is `400` and no event is published

## Message events

### Channel message

- **Given** a validly signed `event_callback` with a `message` event (no `subtype`, no `bot_id`)
- **When** it is posted
- **Then** the response is `200`
- **And** a `SlackMessageReceivedEvent` with kind `MESSAGE` and the event's team, channel, channel type, user, text,
  `ts` and event time is published

### Mention

- **Given** a validly signed `event_callback` with an `app_mention` event
- **When** it is posted
- **Then** a `SlackMessageReceivedEvent` with kind `APP_MENTION` is published

### Thread reply

- **Given** a message event with `thread_ts`
- **When** it is posted
- **Then** the published event's `threadTs()` is that value
- **And** for a message without `thread_ts` it is `null`

### Bot messages are dropped

- **Given** a message event with a `bot_id`
- **When** it is posted
- **Then** the response is `200` and no event is published

### Subtypes are dropped

- **Given** a message event with `subtype` `message_changed`, `message_deleted` or `channel_join`
- **When** it is posted
- **Then** the response is `200` and no event is published

### Other event types are ignored

- **Given** a validly signed `event_callback` with a `reaction_added` event, or a request of an unknown `type`
- **When** it is posted
- **Then** the response is `200` and no event is published

## De-duplication

### Retry is delivered once

- **Given** a message event with `event_id` `Ev1` was processed
- **When** the same event is posted again with `X-Slack-Retry-Num: 1`
- **Then** the response is `200` and no second event is published

### Expired entries are forgotten

- **Given** `Ev1` was processed more than one hour ago (per the injected clock)
- **When** `Ev1` is posted again
- **Then** an event is published

## Asynchronous delivery

### Slow listener does not delay the response

- **Given** a listener that blocks until released
- **When** a message event is posted
- **Then** the response is `200` while the listener is still blocked

### Failing listener does not affect the response

- **Given** a listener that throws an exception
- **When** a message event is posted
- **Then** the response is `200` and the exception is logged
