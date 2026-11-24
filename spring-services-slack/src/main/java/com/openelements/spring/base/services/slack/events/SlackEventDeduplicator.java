package com.openelements.spring.base.services.slack.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the ids of processed Slack events so that Slack's retries are delivered only once.
 *
 * <p>Slack retries an event up to three times within minutes when it does not get a timely
 * acknowledgement. Each id is kept for the configured retention and forgotten afterwards. Correct
 * for a single instance only: a retry that reaches another instance is delivered again.
 */
public final class SlackEventDeduplicator {

  private final Map<String, Instant> seen = new ConcurrentHashMap<>();

  private final Clock clock;

  private final Duration retention;

  /**
   * Creates a deduplicator.
   *
   * @param clock the clock that decides which entries have expired
   * @param retention how long an event id is remembered
   */
  public SlackEventDeduplicator(final Clock clock, final Duration retention) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.retention = Objects.requireNonNull(retention, "retention must not be null");
  }

  /**
   * Records an event id.
   *
   * @param eventId the {@code event_id} of the Slack event
   * @return {@code true} if the id was not seen within the retention, {@code false} for a duplicate
   */
  public boolean firstSeen(final String eventId) {
    Objects.requireNonNull(eventId, "eventId must not be null");
    final Instant now = clock.instant();
    seen.values().removeIf(expiry -> expiry.isBefore(now));
    return seen.putIfAbsent(eventId, now.plus(retention)) == null;
  }

  /**
   * Returns the number of remembered event ids.
   *
   * @return the number of remembered event ids
   */
  int size() {
    return seen.size();
  }
}
