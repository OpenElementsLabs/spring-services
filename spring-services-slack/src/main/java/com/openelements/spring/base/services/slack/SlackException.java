package com.openelements.spring.base.services.slack;

/** Unchecked exception thrown by {@link SlackService} for any Slack-related failure. */
public class SlackException extends RuntimeException {

  public SlackException(final String message) {
    super(message);
  }

  public SlackException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
