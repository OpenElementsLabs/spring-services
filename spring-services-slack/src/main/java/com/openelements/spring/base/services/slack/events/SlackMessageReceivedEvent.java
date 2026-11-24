package com.openelements.spring.base.services.slack.events;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A message a user wrote in Slack, published as a Spring application event after its request was
 * verified.
 *
 * <p>Listen with {@code @EventListener}. If the Slack app subscribes to both {@code app_mention}
 * and a {@code message.*} event, a mention in a channel arrives twice, once of each {@link #kind()}.
 *
 * <p>The signature only proves that the message comes from Slack. Any member of the workspace can
 * write it, so a listener that triggers an action must authorise {@link #userId()} itself.
 *
 * @param kind the Slack event this message was created from
 * @param eventId the {@code event_id} of the Slack event, unique per workspace
 * @param teamId the id of the Slack workspace
 * @param channelId the id of the conversation the message was posted in
 * @param channelType {@code channel}, {@code group}, {@code im} or {@code mpim}; {@code null} for
 *     {@link SlackMessageKind#APP_MENTION}, for which Slack does not send it
 * @param userId the id of the Slack user who wrote the message
 * @param text the message text in Slack's markup; mentions appear as {@code <@U123>}
 * @param ts the timestamp that identifies the message within the conversation
 * @param threadTs the {@code ts} of the thread's parent message, or {@code null} if the message is
 *     not in a thread
 * @param eventTime when Slack created the event
 */
public record SlackMessageReceivedEvent(
    SlackMessageKind kind,
    String eventId,
    String teamId,
    String channelId,
    @Nullable String channelType,
    String userId,
    String text,
    String ts,
    @Nullable String threadTs,
    Instant eventTime) {

  /** Rejects {@code null} for every value except {@code channelType} and {@code threadTs}. */
  public SlackMessageReceivedEvent {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(channelId, "channelId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(ts, "ts must not be null");
    Objects.requireNonNull(eventTime, "eventTime must not be null");
  }
}
