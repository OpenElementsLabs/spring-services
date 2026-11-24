package com.openelements.spring.base.services.slack.events;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for receiving Slack messages, bound from the {@code open-elements.slack.events.*}
 * namespace.
 *
 * <p>{@link #enabled()} defaults to {@code false}: the feature opens a public path, which must be a
 * deliberate decision of the application. Without a {@link #signingSecret()} the application still
 * starts, but the endpoint rejects every request.
 *
 * @param enabled whether the events endpoint is active
 * @param signingSecret the signing secret of the Slack app, used to verify every request; never
 *     commit it, supply it through the environment or secret management
 * @param path the path of the events endpoint, configured as Request URL in the Slack app
 * @param teamId if set, only events of this Slack workspace are accepted; blank counts as unset
 * @param appId if set, only events for this Slack app are accepted; blank counts as unset
 */
@ConfigurationProperties(prefix = "open-elements.slack.events")
public record SlackEventsProperties(
    @DefaultValue("false") boolean enabled,
    @Nullable String signingSecret,
    @DefaultValue(SlackEventsProperties.DEFAULT_PATH) String path,
    @Nullable String teamId,
    @Nullable String appId) {

  /** The path of the events endpoint when {@code open-elements.slack.events.path} is unset. */
  public static final String DEFAULT_PATH = "/slack/events";

  /**
   * Treats a blank {@code teamId} or {@code appId} as unset. An empty environment variable behind
   * {@code team-id} must not turn into a restriction that no event can satisfy.
   */
  public SlackEventsProperties {
    teamId = blankToNull(teamId);
    appId = blankToNull(appId);
  }

  private static @Nullable String blankToNull(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
