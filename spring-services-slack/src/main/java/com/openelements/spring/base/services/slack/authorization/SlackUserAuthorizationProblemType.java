package com.openelements.spring.base.services.slack.authorization;

import com.openelements.spring.base.problem.ProblemType;
import org.springframework.http.HttpStatus;

/**
 * The problem types of the Slack user authorization endpoints.
 *
 * <p>Defined here rather than in {@link com.openelements.spring.base.problem.StandardProblemType},
 * so core knows no slug of an optional module. The slugs are part of the public contract.
 */
public enum SlackUserAuthorizationProblemType implements ProblemType {

  /** The feature is enabled, but not every required property is configured. */
  NOT_CONFIGURED(
      "slack-user-authorization-not-configured",
      HttpStatus.SERVICE_UNAVAILABLE,
      "Slack user authorization not configured"),

  /**
   * The authorization was cancelled, was not started in this browser, or is for another workspace.
   */
  REJECTED(
      "slack-user-authorization-rejected",
      HttpStatus.BAD_REQUEST,
      "Slack user authorization rejected"),

  /** Slack did not issue a usable user token. */
  FAILED(
      "slack-user-authorization-failed",
      HttpStatus.BAD_GATEWAY,
      "Slack user authorization failed");

  private final String slug;

  private final HttpStatus status;

  private final String title;

  SlackUserAuthorizationProblemType(
      final String slug, final HttpStatus status, final String title) {
    this.slug = slug;
    this.status = status;
    this.title = title;
  }

  @Override
  public String slug() {
    return slug;
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String title() {
    return title;
  }
}
