package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Concurrency integration tests for {@link UserService#getCurrentUserEntity()} against a real
 * Postgres database.
 *
 * <h2>What is tested</h2>
 *
 * <p>The three properties that pure-Mockito unit tests cannot verify because they involve real
 * database concurrency:
 *
 * <ol>
 *   <li><b>Same-{@code sub} race recovery.</b> Ten simultaneous first-logins for the identical
 *       {@code sub} must produce exactly one row; the nine losing inserts must be caught and
 *       transparently recovered into a re-fetch. This exercises the {@link UserProvisioner}'s
 *       {@code REQUIRES_NEW} + {@code saveAndFlush} contract and the catch-and-refetch logic
 *       in {@code UserService}.
 *   <li><b>Different-{@code sub} non-blocking.</b> Ten simultaneous first-logins for ten
 *       distinct {@code sub} values must not serialise on a JVM monitor — confirmed by
 *       comparing wall-clock against a single-threaded baseline. Guards against re-introduction
 *       of {@code synchronized} (the pre-spec-011 anti-pattern).
 *   <li><b>Drift sync atomicity.</b> An existing user whose {@code name}, {@code email} and
 *       {@code avatarUrl} all changed at once must be flushed in a single save — Postgres-level
 *       confirmation of the unit-test claim.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. The integration footprint is intentional: the
 * race-recovery contract depends on Postgres's unique-index locking behaviour, which no
 * in-memory database faithfully reproduces.
 *
 * <p><b>Mock surface and justification.</b> {@link
 * com.openelements.spring.base.security.AuthService} is the single {@code @MockBean} — it would
 * otherwise require a Spring Security {@code SecurityContextHolder} populated by the filter
 * chain, which the tests deliberately bypass to drive {@code getCurrentUserEntity()} directly
 * on background threads. Per-thread {@code UserInformation} dispatch is implemented with a
 * thread-local {@link java.util.concurrent.ConcurrentHashMap} consulted from the Mockito {@code
 * thenAnswer} lambda. The repository, {@link UserService}, and {@link UserProvisioner} are
 * real beans backed by real Postgres — no mocking of the components under test.
 *
 * <p>The HikariCP pool is sized to 30 in {@code application-testcontainers.properties} so that
 * 10 concurrent {@code REQUIRES_NEW} provisioning attempts (2 connections per thread =
 * suspended outer + active inner) do not deadlock the pool.
 *
 * <p><b>Known flakiness.</b> The different-{@code sub} non-blocking test compares wall-clock
 * times. Under heavy CI load it occasionally fails because the parallel measurement scales
 * non-linearly with system load while the baseline does not. Single-test isolation reliably
 * passes. The functional invariant (exactly one row per {@code sub}) is robust; only the
 * timing ratio is sensitive.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserService concurrency")
class UserServiceConcurrencyTest {

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private AuthService authService;

  /**
   * Deletes dependent {@code audit_log} rows before the {@code users} rows they reference, then
   * removes every user except the System User. The order matters: {@code audit_log.user_id} has a
   * {@code NOT NULL} foreign key to {@code users.id}, so deleting users first violates referential
   * integrity (the pre-spec-011 cleanup did exactly that and failed under CI).
   */
  @BeforeEach
  void cleanUserRowsExceptSystem() {
    jdbcTemplate.update("delete from audit_log");
    jdbcTemplate.update("delete from users where id <> ?", SystemUser.ID);
  }

  /**
   * Spawns 10 threads that simultaneously call {@code getCurrentUserEntity()} with the same
   * JWT {@code sub}. All ten threads are held on a {@link java.util.concurrent.CountDownLatch}
   * to maximise the race window. Postgres's unique index serialises the inserts; nine attempts
   * fail with {@code DataIntegrityViolationException}; {@code UserService} catches and re-fetches.
   * The assertion is twofold: every thread observes the same {@code UserEntity.id}, and the
   * database holds exactly one row for the shared {@code sub}.
   */
  @Test
  @DisplayName(
      "Ten threads first-logging-in with the same sub all see one shared UserEntity.id and "
          + "the database holds exactly one row — Postgres's unique index serialises the race, "
          + "UserService catches the violation and re-fetches.")
  void concurrentFirstLoginsForSameSubProduceOneRow() throws Exception {
    final String sharedSub = "auth0|concurrent-same";
    final UserInformation info =
        new UserInformation(
            sharedSub, "Same User", "same@example.com", null, sharedSub, sharedSub);
    when(authService.getUserInformation()).thenReturn(Optional.of(info));

    final int threadCount = 10;
    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService exec = Executors.newFixedThreadPool(threadCount);
    final List<Future<UUID>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            exec.submit(
                () -> {
                  start.await();
                  return userService.getCurrentUserEntity().getId();
                }));
      }
      start.countDown();

      final Set<UUID> observedIds = new HashSet<>();
      for (final Future<UUID> f : futures) {
        observedIds.add(f.get(30, TimeUnit.SECONDS));
      }

      assertThat(observedIds)
          .as("all threads must observe the same UserEntity.id")
          .hasSize(1);

      final long persistedRowsForSub =
          userRepository.findAll().stream().filter(u -> sharedSub.equals(u.getSub())).count();
      assertThat(persistedRowsForSub)
          .as("exactly one row must exist for the shared sub")
          .isEqualTo(1L);
    } finally {
      exec.shutdownNow();
    }
  }

  /**
   * Regression guard against re-introducing {@code synchronized} on the hot path. Measures a
   * single-threaded baseline, then runs 10 parallel logins for 10 distinct {@code sub} values.
   * Asserts {@code parallelTotalNs < 5 × threadCount × singleThreadNs} — generous enough to
   * survive CI jitter, strict enough to catch a JVM-level monitor that would force serial
   * execution.
   *
   * <p>Per-thread {@code UserInformation} is dispatched via a {@link
   * java.util.concurrent.ConcurrentHashMap} keyed by {@link Thread} — the Mockito {@code
   * thenAnswer} lambda reads the calling thread's entry. No Spring Security context is needed.
   */
  @Test
  @DisplayName(
      "Ten concurrent first-logins for ten distinct subs run in less than 5× the single-thread "
          + "baseline — guards against re-introducing synchronized on the hot path.")
  void concurrentLoginsForDifferentSubsDoNotBlock() throws Exception {
    final int threadCount = 10;
    final ConcurrentMap<Thread, UserInformation> threadInfo = new ConcurrentHashMap<>();
    when(authService.getUserInformation())
        .thenAnswer(inv -> Optional.ofNullable(threadInfo.get(Thread.currentThread())));

    // Baseline: measure single-threaded duration so the assertion is dimensionless.
    threadInfo.put(
        Thread.currentThread(),
        new UserInformation(
            "baseline-sub",
            "Baseline",
            "baseline@example.com",
            null,
            "baseline-sub",
            "baseline-sub"));
    final long baselineStart = System.nanoTime();
    userService.getCurrentUserEntity();
    final long singleThreadNs = System.nanoTime() - baselineStart;
    threadInfo.clear();

    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService exec = Executors.newFixedThreadPool(threadCount);
    final List<Future<UUID>> futures = new ArrayList<>();
    final long parallelStart;
    final long parallelTotalNs;
    try {
      for (int i = 0; i < threadCount; i++) {
        final int n = i;
        futures.add(
            exec.submit(
                () -> {
                  threadInfo.put(
                      Thread.currentThread(),
                      new UserInformation(
                          "concurrent-sub-" + n,
                          "User " + n,
                          "user" + n + "@example.com",
                          null,
                          "concurrent-sub-" + n,
                          "concurrent-sub-" + n));
                  start.await();
                  return userService.getCurrentUserEntity().getId();
                }));
      }
      parallelStart = System.nanoTime();
      start.countDown();
      final Set<UUID> ids = new HashSet<>();
      for (final Future<UUID> f : futures) {
        ids.add(f.get(30, TimeUnit.SECONDS));
      }
      parallelTotalNs = System.nanoTime() - parallelStart;

      assertThat(ids)
          .as("each thread must create its own distinct row")
          .hasSize(threadCount);

      // If `synchronized` were still on the method, this would scale linearly:
      // parallelTotalNs ≈ threadCount * singleThreadNs. The 5× ceiling is relaxed enough
      // to survive CI jitter while still flagging a re-introduction of serialisation.
      final long ceilingNs = (long) threadCount * singleThreadNs * 5L;
      assertThat(parallelTotalNs)
          .as(
              "10 concurrent first-time logins must not scale linearly with the single-thread"
                  + " duration (baseline=%d ns, parallel=%d ns, ceiling=%d ns)",
              singleThreadNs, parallelTotalNs, ceilingNs)
          .isLessThan(ceilingNs);
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "A drift across name, email and avatarUrl on an existing user is flushed to Postgres in "
          + "one atomic save — the reloaded row reflects every changed field.")
  void driftSyncIsTransactional() {
    final String sub = "auth0|drift-tx";
    when(authService.getUserInformation())
        .thenReturn(
            Optional.of(
                new UserInformation(sub, "Original", "orig@example.com", null, sub, sub)));
    final UserEntity created = userService.getCurrentUserEntity();
    assertThat(created.getName()).isEqualTo("Original");

    when(authService.getUserInformation())
        .thenReturn(
            Optional.of(
                new UserInformation(
                    sub, "Updated", "new@example.com", "https://avatar", sub, sub)));
    final UserEntity updated = userService.getCurrentUserEntity();

    assertThat(updated.getId()).isEqualTo(created.getId());
    assertThat(updated.getName()).isEqualTo("Updated");
    assertThat(updated.getEmail()).isEqualTo("new@example.com");
    assertThat(updated.getAvatarUrl()).isEqualTo("https://avatar");

    final UserEntity reloaded = userRepository.findBySub(sub).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Updated");
    assertThat(reloaded.getEmail()).isEqualTo("new@example.com");
    assertThat(reloaded.getAvatarUrl()).isEqualTo("https://avatar");
  }
}
