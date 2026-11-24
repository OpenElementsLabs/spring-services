# Behaviors: Editing Slack messages of users

## Activation

### Inert by default

- **Given** `spring-services-slack` is on the classpath and `open-elements.slack.user-authorization.enabled` is unset
- **When** the application starts
- **Then** no authorization endpoint, filter chain or token store is registered
- **And** `GET /slack/oauth/authorize` is handled by the default chain (`401` without a JWT)
- **And** `SlackService.sendMessage` works as before

### Edit without the feature

- **Given** the feature is not enabled
- **When** `updateUserMessage(event, text)` is called
- **Then** a `SlackException` is thrown that names `open-elements.slack.user-authorization.enabled`
- **And** no Slack API call is made

### Enabled and configured

- **Given** the feature is enabled with client id, client secret, public base URL and a valid encryption key
- **When** the application starts
- **Then** the endpoints, `slackUserAuthorizationSecurityFilterChain` and `JdbcSlackUserTokenStore` are registered
- **And** no warning about the configuration is logged

### Enabled but not configured

- **Given** the feature is enabled and any of client id, client secret, public base URL or encryption key is missing
- **When** the application starts
- **Then** start-up succeeds
- **And** one warning is logged that names every missing property
- **And** `GET /slack/oauth/authorize` and `GET /slack/oauth/callback` answer `503` with problem type
  `slack-user-authorization-not-configured`
- **And** `updateUserMessage` throws `SlackException`

### Invalid encryption key

- **Given** the feature is enabled and the encryption key is not Base64 or does not decode to exactly 32 bytes
- **When** the application starts
- **Then** it behaves as "Enabled but not configured" and the warning says the key is invalid, without printing it

### Application-provided token store

- **Given** the feature is enabled and the application declares its own `SlackUserTokenStore` bean
- **When** the application starts
- **Then** the application's store is used and `JdbcSlackUserTokenStore` is not registered
- **And** no `DataSource` is required

### No store and no database

- **Given** the feature is enabled, no `SlackUserTokenStore` bean is declared and no `DataSource` exists
- **When** the application starts
- **Then** start-up fails with a message that names both options: provide a `DataSource` or a `SlackUserTokenStore`

### Bot token not required for editing

- **Given** the feature is enabled and configured, a token is stored for the user, and `open-elements.slack.token` is
  unset
- **When** `updateUserMessage(event, text)` is called
- **Then** the message is updated with the user token

## Editing

### Message is updated with the user's token

- **Given** a token is stored for team `T1` and user `U1`
- **When** `updateUserMessage` is called with an event of team `T1`, user `U1`, channel `C1`, ts `1700000000.000100`
  and the text `"Hallo\n\nHello"`
- **Then** `chat.update` is called with the user token, channel `C1`, ts `1700000000.000100`, text `"Hallo\n\nHello"`
  and an empty `blocks` array

### No token stored

- **Given** no token is stored for the event's team and user
- **When** `updateUserMessage` is called
- **Then** `SlackUserAuthorizationRequiredException` is thrown
- **And** its `authorizationUrl()` is `<public-base-url>/slack/oauth/authorize`
- **And** no Slack API call is made

### Exception is a SlackException

- **Given** an application catches `SlackException` around `updateUserMessage`
- **When** no token is stored
- **Then** the `SlackUserAuthorizationRequiredException` is caught by that handler

### Rejected token is deleted

- **Given** a token is stored for the user
- **When** `chat.update` answers with error `token_revoked`, `invalid_auth`, `account_inactive` or `token_expired`
- **Then** the stored token is deleted
- **And** `SlackUserAuthorizationRequiredException` is thrown

### Other Slack errors keep the token

- **Given** a token is stored for the user
- **When** `chat.update` answers with error `message_not_found`
- **Then** `SlackException` (not the authorization subtype) is thrown with the error code in its message
- **And** the token is still stored

### Network error

- **Given** a token is stored for the user
- **When** the Slack API is unreachable
- **Then** `SlackException` is thrown and the token is still stored

### Tokens are separated per workspace and user

- **Given** a token is stored for team `T1` and user `U1` only
- **When** `updateUserMessage` is called with an event of team `T2` and user `U1`, or team `T1` and user `U2`
- **Then** `SlackUserAuthorizationRequiredException` is thrown

### Null arguments

- **When** `updateUserMessage` is called with a `null` event or a `null` text
- **Then** `NullPointerException` is thrown and no Slack API call is made

### Authorization status

- **Given** a token is stored for team `T1` and user `U1`
- **Then** `isUserAuthorized("T1", "U1")` is `true` and `isUserAuthorized("T1", "U2")` is `false`
- **And** after the token was deleted because Slack rejected it, `isUserAuthorized("T1", "U1")` is `false`

### Authorization link without an edit

- **Given** the feature is enabled and configured with public base URL `https://app.example.com`
- **Then** `userAuthorizationUrl()` returns `https://app.example.com/slack/oauth/authorize`

## No loop

### The edit is not delivered as a new message

- **Given** the events feature (spec 026) is enabled and a listener edits every message it receives
- **When** Slack sends the resulting `message` event with subtype `message_changed`
- **Then** no `SlackMessageReceivedEvent` is published

## Authorization endpoints

### Start of the authorization

- **When** `GET /slack/oauth/authorize` is requested without authentication
- **Then** the response is `302` to `https://slack.com/oauth/v2/authorize`
- **And** the location carries `client_id`, `user_scope=chat:write`, `redirect_uri=<public-base-url>/slack/oauth/callback`
  and a `state`
- **And** the location carries no `scope` parameter (no additional bot scopes are requested)
- **And** a cookie `slack_oauth_state` with the same value is set with `HttpOnly`, `Secure`, `SameSite=Lax`,
  `Path=/slack/oauth` and `Max-Age=600`

### Workspace is preselected

- **Given** `open-elements.slack.events.team-id=T1`
- **When** `GET /slack/oauth/authorize` is requested
- **Then** the location also carries `team=T1`

### State is unpredictable

- **When** `GET /slack/oauth/authorize` is requested twice
- **Then** the two `state` values differ

### Successful callback

- **Given** the browser carries the state cookie from `/authorize`
- **When** `GET /slack/oauth/callback?code=c&state=<same state>` is requested and `oauth.v2.access` answers with
  `team.id=T1`, `authed_user.id=U1`, `authed_user.scope=chat:write` and an access token
- **Then** `oauth.v2.access` was called with client id, client secret, code `c` and the same redirect URI
- **And** the token is stored encrypted for `T1`/`U1`
- **And** the response is `200` `text/plain` with "Slack authorization complete. You can close this window."
- **And** the state cookie is cleared

### Authorizing again replaces the token

- **Given** a token is stored for `T1`/`U1`
- **When** the same user completes the callback again with a new token
- **Then** only the new token is stored for `T1`/`U1`

### The user from Slack's answer is stored, not from the request

- **Given** the callback request carries an additional query parameter `user=U2`
- **When** `oauth.v2.access` answers with `authed_user.id=U1`
- **Then** the token is stored for `U1` and nothing for `U2`

### User cancelled

- **When** `GET /slack/oauth/callback?error=access_denied&state=…` is requested
- **Then** the response is `400` with problem type `slack-user-authorization-rejected`
- **And** `oauth.v2.access` is not called and nothing is stored

### State mismatch

- **When** the callback is requested with a `state` that differs from the cookie, without a `state`, or without the
  cookie
- **Then** the response is `400` with problem type `slack-user-authorization-rejected`
- **And** `oauth.v2.access` is not called, nothing is stored, and the state cookie is cleared

### Code exchange fails

- **Given** a matching state
- **When** `oauth.v2.access` answers with `ok=false` (for example `invalid_code`)
- **Then** the response is `502` with problem type `slack-user-authorization-failed` and nothing is stored

### Scope missing

- **Given** a matching state
- **When** `oauth.v2.access` answers without `chat:write` in `authed_user.scope`, or without a user access token
- **Then** the response is `502` with problem type `slack-user-authorization-failed` and nothing is stored

### Token rotation is refused

- **Given** a matching state
- **When** `oauth.v2.access` answers with a `refresh_token` for the user
- **Then** the response is `502` with problem type `slack-user-authorization-failed`, nothing is stored
- **And** a warning says that token rotation must be off in the Slack app

### Foreign workspace

- **Given** `open-elements.slack.events.team-id=T1` and a matching state
- **When** `oauth.v2.access` answers with `team.id=T2`
- **Then** the response is `400` with problem type `slack-user-authorization-rejected` and nothing is stored

### Problem details disabled

- **Given** `openelements.problem-details.enabled=false`
- **When** a callback is rejected
- **Then** the response has the same status and an empty body

### Secrets are never logged

- **When** any authorization request is processed or rejected
- **Then** no log line contains the code, the state, the client secret, the access token or the encryption key

## Security chain

### Only GET on the two paths

- **When** `POST`, `PUT` or `DELETE` is sent to `/slack/oauth/authorize` or `/slack/oauth/callback`
- **Then** the request is denied

### No other path is opened

- **When** `GET /slack/oauth/other` or `GET /slack/oauth` is requested without authentication
- **Then** it is handled by the default chain (`401`)

### Custom path

- **Given** `open-elements.slack.user-authorization.path=/integrations/slack-auth`
- **When** the authorization is started
- **Then** `/integrations/slack-auth/authorize` and `/integrations/slack-auth/callback` are used, the redirect URI and
  the cookie path follow, and `/slack/oauth/*` is not opened

## Token storage

### Tokens are encrypted at rest

- **Given** a token was saved for `T1`/`U1`
- **When** the row in `oe_spring_services.slack_user_tokens` is read directly
- **Then** `encrypted_token` does not contain the token in plain text
- **And** saving the same token twice produces two different ciphertexts (random IV)

### Round trip

- **Given** a token was saved for `T1`/`U1`
- **Then** `find("T1", "U1")` returns it and `find("T1", "U2")` is empty

### Ciphertext is bound to its row

- **Given** tokens are stored for `T1`/`U1` and `T1`/`U2`
- **When** the `encrypted_token` of `U1` is copied into the row of `U2`
- **Then** `find("T1", "U2")` is empty, the row of `U2` is deleted, and a warning names `T1`/`U2`

### Wrong key

- **Given** a token was saved with one key
- **When** the application restarts with another key and the token is looked up
- **Then** the lookup is empty, the row is deleted and a warning is logged without the token

### Delete

- **Given** a token is stored for `T1`/`U1`
- **When** `delete("T1", "U1")` is called
- **Then** `find("T1", "U1")` is empty; deleting a missing token does nothing

## Revocation through events

### tokens_revoked deletes the listed users' tokens

- **Given** the events feature and this feature are enabled, and tokens are stored for `T1`/`U1` and `T1`/`U2`
- **When** a verified `event_callback` of team `T1` with event `tokens_revoked` and `tokens.oauth = ["U1"]` arrives
- **Then** the token of `U1` is deleted and the token of `U2` is kept
- **And** no `SlackMessageReceivedEvent` is published
- **And** the response is `200`

### tokens_revoked without this feature

- **Given** the events feature is enabled and this feature is not
- **When** a verified `tokens_revoked` event arrives
- **Then** the response is `200` and nothing else happens
