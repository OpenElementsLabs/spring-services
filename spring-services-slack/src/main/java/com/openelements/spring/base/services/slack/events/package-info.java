/**
 * Receiving Slack messages through the <a href="https://api.slack.com/apis/events-api">Slack Events
 * API</a>.
 *
 * <p>Slack posts every subscribed event to an endpoint of the application. This package verifies
 * each request, turns messages written by users into a {@link
 * com.openelements.spring.base.services.slack.events.SlackMessageReceivedEvent} and publishes it as
 * a Spring application event. Applications never see HTTP, signatures or Slack's envelope:
 *
 * <pre>{@code
 * @Component
 * class SupportBot {
 *
 *   @EventListener
 *   void onMessage(SlackMessageReceivedEvent message) {
 *     if (message.kind() == SlackMessageKind.APP_MENTION) {
 *       slackService.sendMessage(message.channelId(), "On it.");
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>Configuration</h2>
 *
 * <p>Opt-in, bound from {@code open-elements.slack.events.*} via {@link
 * com.openelements.spring.base.services.slack.events.SlackEventsProperties}:
 *
 * <ul>
 *   <li>{@code enabled} — {@code false} by default; the feature opens a public path.
 *   <li>{@code signing-secret} — the signing secret of the Slack app. Without it the application
 *       starts with a warning, and the endpoint answers every request with {@code 503}. Supply it
 *       through environment variables or secret management, never from committed configuration.
 *   <li>{@code path} — {@code /slack/events} by default; the Request URL of the Slack app.
 *   <li>{@code team-id}, {@code app-id} — optional; accept events of this workspace or app only.
 * </ul>
 *
 * <p>In the Slack app, subscribe to the bot events {@code app_mention} (scope {@code
 * app_mentions:read}) and, for direct messages, {@code message.im} (scope {@code im:history}).
 * {@code message.channels} works too, but delivers every message of every channel the bot is in.
 *
 * <h2>Security</h2>
 *
 * <p>The endpoint sits behind its own filter chain that permits only {@code POST} on the configured
 * path. It authenticates each request by its signature (see {@link
 * com.openelements.spring.base.services.slack.events.SlackSignatureVerifier}): HMAC-SHA256 over the
 * raw body with the signing secret, a timestamp within five minutes, compared in constant time. The
 * body is parsed only afterwards, and limited to 1 MiB. Rejections carry no detail.
 *
 * <p><strong>A verified message is still user input.</strong> The signature proves that the request
 * comes from Slack, not that the Slack user may trigger anything: every member of the workspace,
 * depending on its settings guests too, can write to the bot. A listener that acts on a message must
 * authorise {@link com.openelements.spring.base.services.slack.events.SlackMessageReceivedEvent#userId()}
 * itself, and treat the text like any other external input.
 *
 * <h2>Delivery</h2>
 *
 * <ul>
 *   <li>Only {@code message} and {@code app_mention} events become events; bot messages and
 *       subtypes (edits, deletions, joins) are dropped, so a listener that answers through {@link
 *       com.openelements.spring.base.services.slack.SlackService} does not receive its own answer.
 *   <li>Listeners run on the executor {@code slackEventsTaskExecutor}, so Slack gets its
 *       acknowledgement within three seconds regardless of how long they take. A listener exception
 *       is logged and never reaches the response.
 *   <li>Slack retries are delivered once: event ids are remembered for one hour. This happens per
 *       instance; with several instances, a retry that reaches another instance is delivered again.
 *   <li>If the app subscribes to both {@code app_mention} and a {@code message.*} event, a mention in
 *       a channel arrives twice, once of each {@link
 *       com.openelements.spring.base.services.slack.events.SlackMessageKind}.
 * </ul>
 */
package com.openelements.spring.base.services.slack.events;
