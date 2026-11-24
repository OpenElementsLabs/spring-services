package com.openelements.spring.base.services.slack.events;

import com.openelements.spring.base.problem.ProblemException;

/**
 * Thrown for every request to the events endpoint while no signing secret is configured, so that
 * nothing unverified is ever processed.
 */
final class SlackEventsNotConfiguredException extends ProblemException {

  SlackEventsNotConfiguredException() {
    super(SlackEventsProblemType.NOT_CONFIGURED);
  }
}
