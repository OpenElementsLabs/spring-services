package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link UserService#getCurrentUserEntity()}'s active-gate against a real
 * Postgres database. Complements the mock-level tests in {@link UserServiceTest} with end-to-end
 * persistence behaviour (the entity is flushed, reloaded, and verified across transactions).
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserService — active gate (integration)")
class UserServiceActiveGateIntegrationTest {

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @MockBean private AuthService authService;

  @BeforeEach
  void cleanUsers() {
    final List<UserEntity> toDelete =
        userRepository.findAll().stream()
            .filter(u -> !SystemUser.ID.equals(u.getId()))
            .toList();
    userRepository.deleteAll(toDelete);
  }

  private void stubAuth(final String sub) {
    when(authService.getUserInformation())
        .thenReturn(
            Optional.of(new UserInformation(sub, "User " + sub, sub + "@example.com", null, sub, sub)));
  }

  @Test
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

  @Test
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
  void newUserCreatedByJitIsAlwaysActive() {
    stubAuth("fresh-int");
    final UserEntity user = userService.getCurrentUserEntity();
    assertThat(user.isActive()).isTrue();
  }

  @Test
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
