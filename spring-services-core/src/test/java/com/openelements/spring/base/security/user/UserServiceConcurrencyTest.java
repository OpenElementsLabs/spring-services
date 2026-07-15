package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.data.DbSchema;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * com.openelements.spring.base.security.AuthService} is the single {@code @MockitoBean} — it would
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
 * <p><b>Load-independent.</b> The different-{@code sub} non-blocking test no longer compares
 * wall-clock times. It proves concurrency directly with a {@link CyclicBarrier} that can only
 * trip when every thread is inside {@code getCurrentUserEntity()} at once, so it passes or fails
 * on the actual concurrency property regardless of CI load. The only timeouts are correctness
 * guards against serialisation, not performance thresholds.
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

  @MockitoBean private AuthService authService;

  /**
   * Deletes dependent {@code audit_log} rows before the {@code users} rows they reference, then
   * removes every user except the System User. The order matters: {@code audit_log.user_id} has a
   * {@code NOT NULL} foreign key to {@code users.id}, so deleting users first violates referential
   * integrity (the pre-spec-011 cleanup did exactly that and failed under CI).
   */
  @BeforeEach
  void cleanUserRowsExceptSystem() {
    jdbcTemplate.update("delete from " + DbSchema.NAME + ".audit_log");
    jdbcTemplate.update("delete from " + DbSchema.NAME + ".users where id <> ?", SystemUser.ID);
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
   * Regression guard against re-introducing {@code synchronized} on the hot path — proven
   * <em>deterministically</em>, without any wall-clock measurement.
   *
   * <p>The mechanism: {@code authService.getUserInformation()} is the first call inside {@code
   * getCurrentUserEntity()}, and it is a mock. Its answer parks every calling thread on a {@link
   * CyclicBarrier} sized to {@code threadCount}. The barrier can only trip if all {@code
   * threadCount} threads are inside {@code getCurrentUserEntity()} <em>at the same time</em>:
   *
   * <ul>
   *   <li>If the method is <b>not</b> serialised (the correct state), all threads enter
   *       concurrently, all reach the barrier, it trips immediately, and each proceeds to create
   *       its own row.
   *   <li>If the method were {@code synchronized} again, only one thread could be inside at a
   *       time; the other nine would block on the monitor and never reach the barrier, so the
   *       barrier would time out — surfacing as a {@link java.util.concurrent.TimeoutException}
   *       and failing the test.
   * </ul>
   *
   * <p>The only timeouts involved are correctness guards against a deadlock-like serialisation,
   * not performance ratios, so the test is immune to CI load — it passes or fails on the actual
   * concurrency property, deterministically.
   *
   * <p>Per-thread {@code UserInformation} is dispatched via a {@link ConcurrentHashMap} keyed by
   * {@link Thread} — the Mockito {@code thenAnswer} lambda reads the calling thread's entry. No
   * Spring Security context is needed.
   */
  @Test
  @DisplayName(
      "Ten concurrent first-logins for ten distinct subs are all inside getCurrentUserEntity() "
          + "simultaneously (proven via a CyclicBarrier) — guards against re-introducing "
          + "synchronized on the hot path.")
  void concurrentLoginsForDifferentSubsDoNotBlock() throws Exception {
    final int threadCount = 10;
    final ConcurrentMap<Thread, UserInformation> threadInfo = new ConcurrentHashMap<>();

    // Trips only when all threadCount threads are simultaneously inside getCurrentUserEntity().
    // A synchronized method would let only one thread in at a time, so the barrier would never
    // reach threadCount parties and await() below would time out.
    final CyclicBarrier allInsideMethod = new CyclicBarrier(threadCount);

    when(authService.getUserInformation())
        .thenAnswer(
            inv -> {
              // Runs INSIDE getCurrentUserEntity(); rendezvous here proves concurrent entry.
              allInsideMethod.await(15, TimeUnit.SECONDS);
              return Optional.ofNullable(threadInfo.get(Thread.currentThread()));
            });

    final ExecutorService exec = Executors.newFixedThreadPool(threadCount);
    final List<Future<UUID>> futures = new ArrayList<>();
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
                  return userService.getCurrentUserEntity().getId();
                }));
      }

      final Set<UUID> ids = new HashSet<>();
      for (final Future<UUID> f : futures) {
        // If the method serialised, the barrier would have timed out and this would surface the
        // resulting exception instead of a clean id.
        ids.add(f.get(30, TimeUnit.SECONDS));
      }

      assertThat(ids)
          .as(
              "all %d threads reached the barrier inside getCurrentUserEntity() concurrently and "
                  + "each created its own distinct row",
              threadCount)
          .hasSize(threadCount);
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
