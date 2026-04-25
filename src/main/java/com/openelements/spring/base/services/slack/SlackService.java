package com.openelements.spring.base.services.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text messages to Slack channels via a configured bot user.
 *
 * <p>The Slack bot token is read from the {@code open-elements.slack.token} property. If no token
 * is configured the bean is still registered, but every call to {@link #sendMessage(String,
 * String)} fails fast with a {@link SlackException}.
 */
@Service
public class SlackService {

  private static final Logger LOG = LoggerFactory.getLogger(SlackService.class);

  private static final String TOKEN_NOT_CONFIGURED_MESSAGE =
      "Slack token is not configured (set property 'open-elements.slack.token')";

  @Nullable private final MethodsClient methodsClient;

  public SlackService(final ObjectProvider<MethodsClient> slackMethodsClientProvider) {
    this.methodsClient = slackMethodsClientProvider.getIfAvailable();
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
}
