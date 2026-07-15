package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link UserService#getCurrentUserEntity()}'s active-gate against a real
 * Postgres database. Complements the mock-level tests in {@link UserServiceTest} with end-to-end
 * persistence behaviour (the entity is flushed, reloaded, and verified across transactions).
 *
 * <h2>What is tested</h2>
 *
 * <p>The active-gate contract end-to-end: an existing user whose {@code active} flag has been
 * flipped to {@code false} must be denied on the next interactive login; a subsequent
 * reactivation must restore access against the very same row (same id). A SCIM-pre-provisioned
 * row that is created inactive must not be JIT-bootstrapped — the {@code sub} column stays
 * {@code null} after the denial, so an attacker who knows the {@code externalId} cannot silently
 * claim a deactivated row. Newly JIT-created users start out active. And the disambiguation
 * guard for the email-as-userName fallback path is verified: two distinct JWT subjects that fall
 * back to the same email-as-userName collide on the unique constraint instead of being merged.
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. Each test runs in the application's normal
 * transactional configuration — entities are flushed and reloaded across transactions so the
 * assertions reflect what is durably in the database, not just the session cache.
 *
 * <p><b>Mock-Audit.</b> A single {@code @MockitoBean AuthService} is the only mock. The real
 * {@code AuthService} would require a Spring Security {@code SecurityContextHolder} populated by
 * the filter chain, which these tests deliberately bypass to drive {@code getCurrentUserEntity()}
 * directly. The {@code UserService}, {@link UserRepository}, and {@code UserProvisioner} under
 * test are all real beans backed by real Postgres — no other mocks are introduced.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserService — active gate (integration)")
class UserServiceActiveGateIntegrationTest {

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
  void cleanUsers() {
    jdbcTemplate.update("delete from " + DbSchema.NAME + ".audit_log");
    jdbcTemplate.update("delete from " + DbSchema.NAME + ".users where id <> ?", SystemUser.ID);
  }

  private void stubAuth(final String sub) {
    when(authService.getUserInformation())
        .thenReturn(
            Optional.of(new UserInformation(sub, "User " + sub, sub + "@example.com", null, sub, sub)));
  }

  @Test
  @DisplayName(
      "A user flagged active=false in the database is rejected with AccessDeniedException on the next login.")
  void inactiveUserIsRejectedWithAccessDenied() {
    stubAuth("inactive-int");
    final UserEntity user = userService.getCurrentUserEntity();
    user.setActive(false);
    userRepository.saveAndFlush(user);

    assertThatThrownBy(() -> userService.getCurrentUserEntity())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  @DisplayName(
      "Flipping active back to true restores access against the same UserEntity row — id is preserved.")
  void reactivationRestoresAccess() {
    stubAuth("reactivate-int");
    final UserEntity user = userService.getCurrentUserEntity();
    user.setActive(false);
    userRepository.saveAndFlush(user);

    assertThatThrownBy(() -> userService.getCurrentUserEntity())
        .isInstanceOf(AccessDeniedException.class);

    user.setActive(true);
    userRepository.saveAndFlush(user);

    final UserEntity recovered = userService.getCurrentUserEntity();
    assertThat(recovered.getId()).isEqualTo(user.getId());
    assertThat(recovered.isActive()).isTrue();
  }

  /**
   * Verifies, at the Postgres layer, the same safety property the unit test asserts in isolation:
   * an inactive SCIM-pre-provisioned row matched by {@code externalId} must reject the login
   * <em>before</em> the JIT back-fill writes the JWT {@code sub} onto it. The row is reloaded
   * after the denial and {@code sub} is asserted still {@code null} — proving an attacker who
   * knows the {@code externalId} cannot silently claim a deactivated row by attempting a login.
   */
  @Test
  @DisplayName(
      "An inactive SCIM-pre-provisioned row cannot be JIT-bootstrapped — denial fires before sub is written, the row stays unclaimed.")
  void scimPreProvisionedInactiveUserCannotJitBootstrap() {
    // Simulate a SCIM-pre-provisioned row: externalId + userName set, sub null, active = false
    final UserEntity preProvisioned = new UserEntity();
    preProvisioned.setSub(null);
    preProvisioned.setExternalId("scim-pre-int");
    preProvisioned.setUserName("scim-pre-int");
    preProvisioned.setName("SCIM-pre-provisioned");
    preProvisioned.setActive(false);
    final UserEntity stored = userRepository.saveAndFlush(preProvisioned);
    final var storedId = stored.getId();

    // Now the user attempts an interactive login with a JWT whose sub matches the externalId
    stubAuth("scim-pre-int");

    assertThatThrownBy(() -> userService.getCurrentUserEntity())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("disabled");

    // Critical: sub was NOT written onto the row
    final UserEntity reloaded = userRepository.findById(storedId).orElseThrow();
    assertThat(reloaded.getSub()).isNull();
    assertThat(reloaded.isActive()).isFalse();
  }

  @Test
  @DisplayName("A user provisioned through the JIT path on first login is always created with active=true.")
  void newUserCreatedByJitIsAlwaysActive() {
    stubAuth("fresh-int");
    final UserEntity user = userService.getCurrentUserEntity();
    assertThat(user.isActive()).isTrue();
  }

  /**
   * Pins the disambiguation guard: when two distinct JWT subjects both fall back to email as
   * their {@code preferred_username} (the IdP did not assert one), the second login must collide
   * on the {@code user_name} unique constraint rather than be silently merged into the first
   * user's row. The exact thrown type is either {@link IllegalStateException} (recovery path
   * after a {@code DataIntegrityViolationException} fails to re-fetch by {@code sub}) or the
   * raw {@code DataIntegrityViolationException} — both are accepted because the load-bearing
   * property is "did not merge", not the wrapping.
   */
  @Test
  @DisplayName(
      "Two distinct sub values that both fall back to the same email-as-userName collide on the unique constraint — they are never silently merged.")
  void twoUsersSharingTheSameEmailFallbackCollideOnUserName() {
    // First user logs in with email-as-userName fallback (no preferred_username).
    when(authService.getUserInformation())
        .thenReturn(
            Optional.of(
                new UserInformation(
                    "first-sub",
                    "First",
                    "shared@example.com",
                    null,
                    "shared@example.com",
                    "first-sub")));
    userService.getCurrentUserEntity();

    // Second user logs in with a different sub but the SAME email-as-userName fallback.
    when(authService.getUserInformation())
        .thenReturn(
            Optional.of(
                new UserInformation(
                    "second-sub",
                    "Second",
                    "shared@example.com",
                    null,
                    "shared@example.com",
                    "second-sub")));

    // The unique constraint on user_name surfaces as a DataIntegrityViolationException
    // when the provisioner's saveAndFlush hits the DB. The IllegalStateException wrapper
    // is thrown by the recovery path since the conflict is not on sub but on user_name —
    // the refetch-by-sub returns empty.
    assertThatThrownBy(() -> userService.getCurrentUserEntity())
        .isInstanceOfAny(
            IllegalStateException.class,
            org.springframework.dao.DataIntegrityViolationException.class);
  }
}
