package com.openelements.spring.base.services.slack;

/** Unchecked exception thrown by {@link SlackService} for any Slack-related failure. */
public class SlackException extends RuntimeException {

  /**
   * Creates a new exception with the given detail message.
   *
   * @param message the detail message describing the Slack-related failure
   */
  public SlackException(final String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given detail message and underlying cause.
   *
   * @param message the detail message describing the Slack-related failure
   * @param cause the underlying cause of the failure
   */
  public SlackException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
