/**
 * Reusable Slack messaging integration.
 *
 * <p>This package provides {@link com.openelements.spring.base.services.slack.SlackService}, a thin
 * {@code @Service} wrapper around the Slack Web API ({@code chat.postMessage}) that posts
 * plain-text messages (including links) to Slack channels as a configured bot user.
 *
 * <h2>Configuration</h2>
 *
 * <p>The Slack bot token is bound from {@code open-elements.slack.token} via {@link
 * com.openelements.spring.base.services.slack.SlackProperties}. The token must be supplied through
 * environment variables or secret management — never committed to source control.
 *
 * <p>If the token is missing or blank, the bean is still registered but a warning is logged at
 * startup, and every call to {@link
 * com.openelements.spring.base.services.slack.SlackService#sendMessage(String, String)} throws
 * {@link com.openelements.spring.base.services.slack.SlackException}.
 *
 * <h2>Scope</h2>
 *
 * <p>The service is intentionally minimal: a single {@code sendMessage(channel, text)} method, no
 * threading, no retries, no rich formatting. Triggering logic — when and why a message is sent — is
 * the responsibility of the consuming application.
 */
package com.openelements.spring.base.services.slack;
