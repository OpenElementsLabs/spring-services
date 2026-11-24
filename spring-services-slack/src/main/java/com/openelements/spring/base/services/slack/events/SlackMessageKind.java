package com.openelements.spring.base.services.slack.events;

/** The Slack event a {@link SlackMessageReceivedEvent} was created from. */
public enum SlackMessageKind {

  /**
   * A {@code message} event: a message in a channel, group or direct conversation the bot is
   * subscribed to.
   */
  MESSAGE,

  /** An {@code app_mention} event: a message that mentions the bot. */
  APP_MENTION
}
