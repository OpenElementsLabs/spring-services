package com.openelements.spring.base.services.slack.events;

import com.openelements.spring.base.problem.ProblemType;
import org.springframework.http.HttpStatus;

/**
 * The problem types of the Slack events endpoint.
 *
 * <p>Defined here rather than in {@link com.openelements.spring.base.problem.StandardProblemType},
 * so core knows no slug of an optional module. The slugs are part of the public contract.
 */
public enum SlackEventsProblemType implements ProblemType {

  /** The feature is enabled, but no signing secret is configured. */
  NOT_CONFIGURED(
      "slack-events-not-configured", HttpStatus.SERVICE_UNAVAILABLE, "Slack events not configured"),

  /** The request body exceeds the size limit. */
  TOO_LARGE("slack-event-too-large", HttpStatus.CONTENT_TOO_LARGE, "Slack event too large"),

  /**
   * The request is not verifiably from Slack, or is for another workspace or app. Deliberately one
   * type for every reason, so a caller cannot tell which check failed.
   */
  SIGNATURE_INVALID(
      "slack-signature-invalid", HttpStatus.UNAUTHORIZED, "Slack signature invalid");

  private final String slug;

  private final HttpStatus status;

  private final String title;

  SlackEventsProblemType(final String slug, final HttpStatus status, final String title) {
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
