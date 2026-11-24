package com.openelements.spring.base.services.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SlackEventDeduplicator")
class SlackEventDeduplicatorTest {

  private static final Duration RETENTION = Duration.ofHours(1);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T12:00:00Z"));

  private final SlackEventDeduplicator deduplicator =
      new SlackEventDeduplicator(clock, RETENTION);

  @Test
  @DisplayName("Reports an event id as new the first time and as duplicate afterwards")
  void secondCallIsDuplicate() {
    assertThat(deduplicator.firstSeen("Ev1")).isTrue();
    assertThat(deduplicator.firstSeen("Ev1")).isFalse();
  }

  @Test
  @DisplayName("Treats different event ids independently")
  void differentIdsAreIndependent() {
    assertThat(deduplicator.firstSeen("Ev1")).isTrue();
    assertThat(deduplicator.firstSeen("Ev2")).isTrue();
  }

  @Test
  @DisplayName("Still reports a duplicate exactly at the end of the retention")
  void duplicateAtEndOfRetention() {
    deduplicator.firstSeen("Ev1");
    clock.advance(RETENTION);

    assertThat(deduplicator.firstSeen("Ev1")).isFalse();
  }

  @Test
  @DisplayName("Reports an event id as new again once the retention has passed")
  void forgetsAfterRetention() {
    deduplicator.firstSeen("Ev1");
    clock.advance(RETENTION.plusSeconds(1));

    assertThat(deduplicator.firstSeen("Ev1")).isTrue();
  }

  @Test
  @DisplayName("Removes expired entries when another event arrives")
  void removesExpiredEntries() {
    deduplicator.firstSeen("Ev1");
    clock.advance(Duration.ofMinutes(30));
    deduplicator.firstSeen("Ev2");
    clock.advance(Duration.ofMinutes(31));

    deduplicator.firstSeen("Ev3");

    assertThat(deduplicator.size()).isEqualTo(2);
    assertThat(deduplicator.firstSeen("Ev2")).isFalse();
  }

  @Test
  @DisplayName("Reports exactly one caller as first when the same id arrives concurrently")
  void concurrentCallsYieldOneFirst() throws Exception {
    final int threads = 16;
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      final List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return deduplicator.firstSeen("Ev1");
                }));
      }
      start.countDown();

      int first = 0;
      for (final Future<Boolean> result : results) {
        if (result.get()) {
          first++;
        }
      }
      assertThat(first).isEqualTo(1);
    }
  }
}
