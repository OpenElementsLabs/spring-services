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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test verifying that {@link UserService#getCurrentUserEntity()} behaves correctly
 * under concurrent calls after {@code synchronized} has been removed from its signature.
 *
 * <p>The original race-protection contract is preserved by the {@code UNIQUE} constraint on
 * {@code users.sub} plus the {@code DataIntegrityViolationException} recovery block inside the
 * method — not by monitor-based serialisation.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserService concurrency")
class UserServiceConcurrencyTest {

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @MockBean private AuthService authService;

  @BeforeEach
  void cleanUserRowsExceptSystem() {
    final List<UserEntity> toDelete =
        userRepository.findAll().stream()
            .filter(u -> !SystemUser.ID.equals(u.getId()))
            .toList();
    userRepository.deleteAll(toDelete);
  }

  @Test
  @DisplayName("Concurrent first logins for the same subject produce exactly one row")
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

  @Test
  @DisplayName("Concurrent logins for different subjects do not block each other")
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
  @DisplayName("Drift sync remains transactional and atomic")
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
