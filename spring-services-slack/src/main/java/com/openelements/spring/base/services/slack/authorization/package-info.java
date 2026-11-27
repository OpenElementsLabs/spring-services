/**
 * Editing Slack messages of users, for example to add a translation below what a user wrote.
 *
 * <p>Slack lets only the author of a message edit it, so the bot token cannot. Each user allows it
 * once: the application sends the link of {@link
 * com.openelements.spring.base.services.slack.SlackService#userAuthorizationUrl()}, the user
 * confirms Slack's authorization dialog, and {@link
 * com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationEndpoint} stores
 * a <em>user token</em> for that user. From then on {@link
 * com.openelements.spring.base.services.slack.SlackService#updateUserMessage} edits the user's
 * messages in their name:
 *
 * <pre>{@code
 * @EventListener
 * void on(SlackMessageReceivedEvent message) {
 *   final String text = message.text() + "\n\n:gb: " + translator.toEnglish(message.text());
 *   try {
 *     slackService.updateUserMessage(message, text);
 *   } catch (SlackUserAuthorizationRequiredException e) {
 *     slackService.sendMessage(message.userId(), "Allow me to add translations: " + e.authorizationUrl());
 *   }
 * }
 * }</pre>
 *
 * <h2>Configuration</h2>
 *
 * <p>Opt-in, bound from {@code open-elements.slack.user-authorization.*} via {@link
 * com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationProperties}:
 * {@code enabled}, {@code client-id}, {@code client-secret}, {@code public-base-url}, {@code path}
 * ({@code /slack/oauth} by default) and {@code token-encryption-key} (Base64, 32 bytes). Without the
 * required values the application starts with a warning and the feature refuses to work.
 *
 * <p>In the Slack app, add the redirect URL {@code <public-base-url>/slack/oauth/callback} and the
 * <em>user</em> token scope {@code chat:write}, and keep token rotation off. Subscribing to the bot
 * event {@code tokens_revoked} removes a token as soon as its user withdraws the permission.
 *
 * <h2>Security</h2>
 *
 * <p>A user token with {@code chat:write} lets its holder post and edit as that user in every
 * conversation the user can write to. Therefore:
 *
 * <ul>
 *   <li>Only this one scope is requested, and the token is used for nothing but {@code chat.update}.
 *   <li>Tokens are stored encrypted with AES-256-GCM ({@link
 *       com.openelements.spring.base.services.slack.authorization.SlackTokenCipher}), bound to their
 *       workspace and user. A database dump or backup without the key is useless.
 *   <li>The token is stored for the user Slack names when the code is exchanged, never for a user
 *       named in a request: whoever opens the link can only authorize as themselves.
 *   <li>A state cookie protects the callback against requests that were not started in the same
 *       browser.
 *   <li>A token Slack rejects is removed; the next edit asks the user again.
 *   <li>Code, state, tokens, the client secret and the key are never logged.
 * </ul>
 *
 * <p>The endpoints sit behind their own filter chain that permits only {@code GET} on exactly the
 * two paths.
 */
package com.openelements.spring.base.services.slack.authorization;
