# Behaviors: Slack Messaging Service

## Configuration

### Application starts without Slack token

- **Given** no `open-elements.slack.token` property is configured
- **When** the application starts
- **Then** the application starts successfully
- **And** a WARNING is logged indicating that the Slack token is not configured

### Application starts with Slack token

- **Given** `open-elements.slack.token` is set to a valid bot token
- **When** the application starts
- **Then** the application starts successfully
- **And** no warning about missing Slack token is logged

### Application starts with blank Slack token

- **Given** `open-elements.slack.token` is set to an empty or blank string
- **When** the application starts
- **Then** the application starts successfully
- **And** a WARNING is logged indicating that the Slack token is not configured

## Sending Messages — Happy Path

### Send message to channel by name

- **Given** a valid Slack bot token is configured
- **And** the bot has access to the channel `#general`
- **When** `sendMessage("#general", "Hello World")` is called
- **Then** the message "Hello World" is posted to `#general` as the bot user
- **And** no exception is thrown

### Send message to channel by ID

- **Given** a valid Slack bot token is configured
- **And** the bot has access to a channel with ID `C01234ABCDE`
- **When** `sendMessage("C01234ABCDE", "Hello World")` is called
- **Then** the message "Hello World" is posted to the channel with ID `C01234ABCDE`
- **And** no exception is thrown

### Send message containing a link

- **Given** a valid Slack bot token is configured
- **When** `sendMessage("#general", "Check https://example.com for details")` is called
- **Then** the message is posted with the link rendered as clickable by Slack

## Sending Messages — Error Cases

### Send message without configured token

- **Given** no `open-elements.slack.token` property is configured
- **When** `sendMessage("#general", "Hello")` is called
- **Then** a `SlackException` is thrown
- **And** the exception message indicates that the token is not configured

### Send message with blank token

- **Given** `open-elements.slack.token` is set to a blank string
- **When** `sendMessage("#general", "Hello")` is called
- **Then** a `SlackException` is thrown
- **And** the exception message indicates that the token is not configured

### Send message to invalid channel

- **Given** a valid Slack bot token is configured
- **When** `sendMessage("#nonexistent-channel", "Hello")` is called
- **And** the Slack API returns an error (e.g., `channel_not_found`)
- **Then** a `SlackException` is thrown
- **And** the exception message contains the Slack error string

### Send message with revoked token

- **Given** the configured Slack bot token has been revoked
- **When** `sendMessage("#general", "Hello")` is called
- **And** the Slack API returns an authentication error
- **Then** a `SlackException` is thrown

### Network error during send

- **Given** a valid Slack bot token is configured
- **When** `sendMessage("#general", "Hello")` is called
- **And** a network error occurs (e.g., timeout, DNS failure)
- **Then** a `SlackException` is thrown
- **And** the original `IOException` is available as the exception cause

## Input Validation

### Null channel parameter

- **Given** a valid Slack bot token is configured
- **When** `sendMessage(null, "Hello")` is called
- **Then** a `NullPointerException` is thrown

### Null text parameter

- **Given** a valid Slack bot token is configured
- **When** `sendMessage("#general", null)` is called
- **Then** a `NullPointerException` is thrown
