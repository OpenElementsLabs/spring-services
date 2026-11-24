package com.openelements.spring.base.services.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("SlackEventMapper")
class SlackEventMapperTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  @DisplayName("Maps a channel message with all its values")
  void mapsChannelMessage() {
    assertThat(SlackEventMapper.map(fixture("message.json")))
        .contains(
            new SlackMessageReceivedEvent(
                SlackMessageKind.MESSAGE,
                "Ev0PV52K21",
                "T061EG9R6",
                "C2147483705",
                "channel",
                "U2147483697",
                "Hello world",
                "1355517523.000005",
                null,
                Instant.ofEpochSecond(1355517523)));
  }

  @Test
  @DisplayName("Maps a mention as APP_MENTION without channel type")
  void mapsMention() {
    assertThat(SlackEventMapper.map(fixture("app-mention.json")))
        .hasValueSatisfying(
            event -> {
              assertThat(event.kind()).isEqualTo(SlackMessageKind.APP_MENTION);
              assertThat(event.channelId()).isEqualTo("C0LAN2Q65");
              assertThat(event.userId()).isEqualTo("U061F7AUR");
              assertThat(event.channelType()).isNull();
            });
  }

  @Test
  @DisplayName("Carries the thread_ts of a reply in a thread")
  void mapsThreadReply() {
    assertThat(SlackEventMapper.map(fixture("thread-reply.json")))
        .hasValueSatisfying(
            event -> assertThat(event.threadTs()).isEqualTo("1355517523.000005"));
  }

  @Test
  @DisplayName("Treats a message without text as empty text")
  void mapsMissingTextAsEmpty() {
    final ObjectNode envelope = fixture("message.json");
    ((ObjectNode) envelope.get("event")).remove("text");

    assertThat(SlackEventMapper.map(envelope))
        .hasValueSatisfying(event -> assertThat(event.text()).isEmpty());
  }

  @Test
  @DisplayName("Drops a message that carries a bot_id")
  void dropsBotMessage() {
    assertThat(SlackEventMapper.map(fixture("bot-message.json"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"message_changed", "message_deleted", "channel_join"})
  @DisplayName("Drops messages with a subtype")
  void dropsSubtypes(final String subtype) {
    final ObjectNode envelope = fixture("message.json");
    ((ObjectNode) envelope.get("event")).put("subtype", subtype);

    assertThat(SlackEventMapper.map(envelope)).isEmpty();
  }

  @Test
  @DisplayName("Ignores event types other than message and app_mention")
  void ignoresOtherEventTypes() {
    assertThat(SlackEventMapper.map(fixture("reaction-added.json"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"channel", "user", "ts"})
  @DisplayName("Drops a message that lacks a required event field")
  void dropsIncompleteEvent(final String field) {
    final ObjectNode envelope = fixture("message.json");
    ((ObjectNode) envelope.get("event")).remove(field);

    assertThat(SlackEventMapper.map(envelope)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"event_id", "team_id", "event_time"})
  @DisplayName("Drops a message whose envelope lacks a required field")
  void dropsIncompleteEnvelope(final String field) {
    final ObjectNode envelope = fixture("message.json");
    envelope.remove(field);

    assertThat(SlackEventMapper.map(envelope)).isEmpty();
  }

  private static ObjectNode fixture(final String name) {
    final JsonNode node = MAPPER.readTree(SlackFixtures.load(name));
    return (ObjectNode) node;
  }
}
