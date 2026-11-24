package com.openelements.spring.base.services.slack.events;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Turns the envelope of a verified {@code event_callback} into a {@link SlackMessageReceivedEvent}.
 *
 * <p>Only messages written by a user become an event: {@code message} and {@code app_mention}
 * without {@code bot_id} and without {@code subtype}. Ignoring bot messages keeps a listener that
 * answers through {@code SlackService} from receiving its own answer; subtypes are edits,
 * deletions, joins and similar notifications, not new messages.
 */
final class SlackEventMapper {

  private SlackEventMapper() {}

  /**
   * Maps an envelope.
   *
   * @param envelope the parsed body of an {@code event_callback} request
   * @return the event, or empty if the envelope is no user message or lacks a required field
   */
  static Optional<SlackMessageReceivedEvent> map(final JsonNode envelope) {
    final JsonNode event = envelope.path("event");
    final SlackMessageKind kind = kindOf(text(event, "type"));
    if (kind == null || event.has("bot_id") || event.has("subtype")) {
      return Optional.empty();
    }
    final String eventId = text(envelope, "event_id");
    final String teamId = text(envelope, "team_id");
    final String channelId = text(event, "channel");
    final String userId = text(event, "user");
    final String ts = text(event, "ts");
    final JsonNode eventTime = envelope.path("event_time");
    if (eventId == null
        || teamId == null
        || channelId == null
        || userId == null
        || ts == null
        || !eventTime.isNumber()) {
      return Optional.empty();
    }
    return Optional.of(
        new SlackMessageReceivedEvent(
            kind,
            eventId,
            teamId,
            channelId,
            text(event, "channel_type"),
            userId,
            Optional.ofNullable(text(event, "text")).orElse(""),
            ts,
            text(event, "thread_ts"),
            Instant.ofEpochSecond(eventTime.asLong())));
  }

  private static @Nullable SlackMessageKind kindOf(final @Nullable String type) {
    return switch (type) {
      case "message" -> SlackMessageKind.MESSAGE;
      case "app_mention" -> SlackMessageKind.APP_MENTION;
      case null, default -> null;
    };
  }

  /**
   * Reads a string field.
   *
   * @param node the object to read from
   * @param field the field name
   * @return the value, or {@code null} if the field is absent or not a string
   */
  static @Nullable String text(final JsonNode node, final String field) {
    final JsonNode value = node.get(field);
    return value != null && value.isString() ? value.stringValue() : null;
  }
}
