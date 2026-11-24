package com.openelements.spring.base.services.slack;

import java.util.Objects;

/**
 * Thrown by {@link SlackService#updateUserMessage} when the user has not allowed the application to
 * edit their messages, or Slack no longer accepts that permission.
 *
 * <p>The application decides whether and how to ask the user, typically by sending {@link
 * #authorizationUrl()} as a direct message. A subclass of {@link SlackException}, so a handler for
 * every Slack failure also covers this case.
 */
public class SlackUserAuthorizationRequiredException extends SlackException {

  private final String authorizationUrl;

  /**
   * Creates the exception.
   *
   * @param teamId the id of the Slack workspace
   * @param userId the id of the Slack user who has to authorize
   * @param authorizationUrl the link the user opens to authorize
   */
  public SlackUserAuthorizationRequiredException(
      final String teamId, final String userId, final String authorizationUrl) {
    super("Slack user " + userId + " in workspace " + teamId + " has not authorized editing");
    this.authorizationUrl =
        Objects.requireNonNull(authorizationUrl, "authorizationUrl must not be null");
  }

  /**
   * Returns the link the user opens to allow the application to edit their messages.
   *
   * @return the absolute URL
   */
  public String authorizationUrl() {
    return authorizationUrl;
  }
}
