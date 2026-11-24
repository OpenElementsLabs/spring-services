package com.openelements.spring.base.services.slack;

import com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationProperties;
import com.openelements.spring.base.services.slack.authorization.SlackUserTokenStore;
import com.openelements.spring.base.services.slack.events.SlackMessageReceivedEvent;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text messages to Slack channels via a configured bot user, and edits messages of
 * users who allowed it.
 *
 * <p>The Slack bot token is read from the {@code open-elements.slack.token} property. If no token
 * is configured the bean is still registered, but every call to {@link #sendMessage(String,
 * String)} fails fast with a {@link SlackException}.
 *
 * <p>Editing a user's message ({@link #updateUserMessage}) needs that user's own token, because
 * Slack lets only the author edit a message. It is enabled with {@code
 * open-elements.slack.user-authorization.enabled}; each user authorizes once through the link of
 * {@link #userAuthorizationUrl()}.
 */
@Service
public class SlackService {

  private static final Logger LOG = LoggerFactory.getLogger(SlackService.class);

  private static final String TOKEN_NOT_CONFIGURED_MESSAGE =
      "Slack token is not configured (set property 'open-elements.slack.token')";

  /** Replaces the blocks of the edited message, so that Slack displays the new text. */
  private static final String NO_BLOCKS = "[]";

  /** Slack errors after which a user token will never work again. */
  private static final Set<String> REJECTED_TOKEN_ERRORS =
      Set.of("token_revoked", "invalid_auth", "account_inactive", "token_expired");

  @Nullable private final MethodsClient methodsClient;

  private final Slack slack;

  private final ObjectProvider<SlackUserAuthorizationProperties> userAuthorizationProperties;

  private final ObjectProvider<SlackUserTokenStore> userTokenStore;

  /**
   * Creates the service, resolving the Slack client if one is available.
   *
   * @param slackMethodsClientProvider provider for the Slack {@link MethodsClient}; the client is
   *     absent when no Slack token is configured
   * @param slack the Slack SDK, used for calls with a user token
   * @param userAuthorizationProperties the configuration for editing user messages; absent while
   *     the feature is not enabled
   * @param userTokenStore the stored user tokens; absent while the feature is not configured
   */
  public SlackService(
      final ObjectProvider<MethodsClient> slackMethodsClientProvider,
      final Slack slack,
      final ObjectProvider<SlackUserAuthorizationProperties> userAuthorizationProperties,
      final ObjectProvider<SlackUserTokenStore> userTokenStore) {
    this.methodsClient = slackMethodsClientProvider.getIfAvailable();
    this.slack = Objects.requireNonNull(slack, "slack must not be null");
    this.userAuthorizationProperties =
        Objects.requireNonNull(
            userAuthorizationProperties, "userAuthorizationProperties must not be null");
    this.userTokenStore = Objects.requireNonNull(userTokenStore, "userTokenStore must not be null");
  }

  @PostConstruct
  void warnIfNotConfigured() {
    if (methodsClient == null) {
      LOG.warn(
          "Slack token is not configured. Calls to SlackService.sendMessage will fail. "
              + "Set property 'open-elements.slack.token' to enable Slack messaging.");
    }
  }

  /**
   * Posts {@code text} as a top-level message to {@code channel} as the configured bot user.
   *
   * @param channel a channel name (e.g. {@code #general}) or channel ID (e.g. {@code C01234ABCDE})
   * @param text the message body; links are rendered by Slack
   * @throws SlackException if no token is configured, the Slack API returns an error, or a network
   *     error occurs
   * @throws NullPointerException if {@code channel} or {@code text} is {@code null}
   */
  public void sendMessage(@NonNull final String channel, @NonNull final String text) {
    Objects.requireNonNull(channel, "channel must not be null");
    Objects.requireNonNull(text, "text must not be null");
    if (methodsClient == null) {
      throw new SlackException(TOKEN_NOT_CONFIGURED_MESSAGE);
    }
    try {
      final ChatPostMessageResponse response =
          methodsClient.chatPostMessage(req -> req.channel(channel).text(text));
      if (!response.isOk()) {
        throw new SlackException(
            "Slack API returned error for channel '" + channel + "': " + response.getError());
      }
    } catch (final SlackApiException e) {
      throw new SlackException("Slack API call failed for channel '" + channel + "'", e);
    } catch (final IOException e) {
      throw new SlackException("Network error while sending Slack message to '" + channel + "'", e);
    }
  }

  /**
   * Replaces the text of a message a user wrote, in that user's name. Slack shows the message with
   * the new text and marks it as edited.
   *
   * <p>The previous content is replaced completely, including the formatting Slack keeps apart from
   * the text; to extend the message, pass {@code message.text()} plus the addition. Formatting
   * survives as far as the text carries it ({@code *bold*}, {@code <@U123>}, links).
   *
   * <p>The edit does not reach a listener as a new message: Slack reports it as a {@code
   * message_changed} event, which is not published.
   *
   * @param message the message to edit, as received
   * @param text the new text of the message
   * @throws SlackUserAuthorizationRequiredException if the user has not authorized editing, or
   *     Slack no longer accepts their token; the stored token is removed in that case
   * @throws SlackException if the feature is not enabled or not configured, the Slack API returns
   *     another error, or a network error occurs
   * @throws NullPointerException if {@code message} or {@code text} is {@code null}
   */
  public void updateUserMessage(final SlackMessageReceivedEvent message, final String text) {
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(text, "text must not be null");
    final SlackUserAuthorizationProperties properties = userAuthorization();
    final SlackUserTokenStore store = tokenStore();
    final String teamId = message.teamId();
    final String userId = message.userId();
    final String token =
        store
            .find(teamId, userId)
            .orElseThrow(() -> authorizationRequired(teamId, userId, properties));
    final ChatUpdateResponse response;
    try {
      response =
          slack
              .methods(token)
              .chatUpdate(
                  req ->
                      req.channel(message.channelId())
                          .ts(message.ts())
                          .text(text)
                          .blocksAsString(NO_BLOCKS));
    } catch (final SlackApiException e) {
      throw new SlackException(
          "Slack API call failed for message " + message.ts() + " in " + message.channelId(), e);
    } catch (final IOException e) {
      throw new SlackException(
          "Network error while editing Slack message "
              + message.ts()
              + " in "
              + message.channelId(),
          e);
    }
    if (response.isOk()) {
      return;
    }
    if (REJECTED_TOKEN_ERRORS.contains(response.getError())) {
      LOG.warn(
          "Slack rejected the user token of user {} in workspace {} ({}), removed it",
          userId,
          teamId,
          response.getError());
      store.delete(teamId, userId);
      throw authorizationRequired(teamId, userId, properties);
    }
    throw new SlackException(
        "Slack API returned error for message "
            + message.ts()
            + " in "
            + message.channelId()
            + ": "
            + response.getError());
  }

  /**
   * Returns whether a user has authorized editing their messages. A user token Slack has rejected
   * since counts as not authorized once an edit found out.
   *
   * @param teamId the id of the Slack workspace
   * @param userId the id of the Slack user
   * @return {@code true} if a token is stored for the user
   * @throws SlackException if the feature is not enabled or not configured
   * @throws NullPointerException if {@code teamId} or {@code userId} is {@code null}
   */
  public boolean isUserAuthorized(final String teamId, final String userId) {
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    userAuthorization();
    return tokenStore().find(teamId, userId).isPresent();
  }

  /**
   * Returns the link a user opens to allow the application to edit their messages.
   *
   * @return the absolute URL
   * @throws SlackException if the feature is not enabled or not configured
   */
  public String userAuthorizationUrl() {
    return Objects.requireNonNull(userAuthorization().authorizationUrl());
  }

  private SlackUserAuthorizationProperties userAuthorization() {
    final SlackUserAuthorizationProperties properties =
        userAuthorizationProperties.getIfAvailable();
    if (properties == null) {
      throw new SlackException(
          "Editing user messages is not enabled (set property '"
              + SlackUserAuthorizationProperties.PREFIX
              + ".enabled=true')");
    }
    final List<String> missing = properties.missingProperties();
    if (!missing.isEmpty()) {
      throw new SlackException(
          "Editing user messages is not configured, missing: " + String.join(", ", missing));
    }
    return properties;
  }

  private SlackUserTokenStore tokenStore() {
    final SlackUserTokenStore store = userTokenStore.getIfAvailable();
    if (store == null) {
      throw new SlackException("No SlackUserTokenStore is available");
    }
    return store;
  }

  private static SlackUserAuthorizationRequiredException authorizationRequired(
      final String teamId,
      final String userId,
      final SlackUserAuthorizationProperties properties) {
    return new SlackUserAuthorizationRequiredException(
        teamId, userId, Objects.requireNonNull(properties.authorizationUrl()));
  }
}
