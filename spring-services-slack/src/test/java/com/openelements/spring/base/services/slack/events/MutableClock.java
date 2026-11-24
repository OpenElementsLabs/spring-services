package com.openelements.spring.base.services.slack.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that only moves when a test advances it. */
public final class MutableClock extends Clock {

  private volatile Instant now;

  /**
   * Creates a clock that stands at the given instant.
   *
   * @param start the initial instant
   */
  public MutableClock(final Instant start) {
    this.now = start;
  }

  /**
   * Moves the clock forward.
   *
   * @param duration how far to move
   */
  public void advance(final Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(final ZoneId zone) {
    throw new UnsupportedOperationException();
  }
}
