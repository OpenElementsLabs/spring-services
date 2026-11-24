# Upgrade prompt: spring-services 2.0.0 → 2.1.0

`spring-services` 2.1.0 brings the changes below. Every section stands on its own — apply only the
ones that affect you:

| Change | Spec | Nature |
| --- | --- | --- |
| Optional **editing of users' Slack messages** | 027 | new optional feature, off by default, one new table |

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

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

## Prompt

You are working inside a Spring Boot service that depends on `spring-services-slack` (directly or via
`spring-services-all`) and wants listeners to edit the messages users write.

### 1. Apply the database migration

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

### 2. Set up the Slack app

In the Slack app's settings, under *OAuth & Permissions*:

- Add the redirect URL `https://<host>/slack/oauth/callback`.
- Add the **user** token scope `chat:write` (not a bot scope).
- Keep *Token Rotation* off. The callback refuses tokens from an app with rotation on.

Optionally, if incoming messages (spec 026) are enabled, subscribe to the bot event `tokens_revoked`,
so a token is removed as soon as its user withdraws the permission.

### 3. Configure the feature

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

### 4. Edit messages in a listener

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

### 5. Verify

- The application starts without the warning "Editing Slack messages of users is enabled, but not
  configured".
- Opening `https://<host>/slack/oauth/authorize` leads to Slack's dialog; confirming it ends on
  "Slack authorization complete".
- The next message of that user is shown with the new text and "(edited)".

### Guard rails / Don't do this

- **Never commit the client secret or the encryption key.** Anyone with a database dump and the key can
  act as every authorized user.
- **Changing the key makes every stored token unreadable.** The tokens are removed on use and the users
  have to authorize again.
- **Do not request more user scopes than `chat:write`.** The module needs no other, and every scope
  widens what a leaked token allows.
- **A verified message is still user input** (spec 026): authorise `userId()` before acting on it.
