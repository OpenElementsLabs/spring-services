package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Schema-level integration tests covering the new {@code external_id}, {@code user_name}, and
 * {@code active} columns plus the relaxed {@code sub} constraint introduced by spec 012.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserRepository — spec 012 schema")
class UserRepositorySchemaTest {

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void cleanUserRowsExceptSystem() {
    final List<UserEntity> toDelete =
        userRepository.findAll().stream()
            .filter(u -> !SystemUser.ID.equals(u.getId()))
            .toList();
    userRepository.deleteAll(toDelete);
  }

  private UserEntity buildUser(
      final String sub, final String externalId, final String userName, final String displayName) {
    final UserEntity user = new UserEntity();
    user.setSub(sub);
    user.setExternalId(externalId);
    user.setUserName(userName);
    user.setName(displayName);
    return user;
  }

  @Test
  void findByExternalIdReturnsPresentForKnownId() {
    userRepository.saveAndFlush(buildUser("sub-a", "ext-a", "user-a", "Alice"));

    final Optional<UserEntity> result = userRepository.findByExternalId("ext-a");

    assertThat(result).isPresent();
    assertThat(result.get().getUserName()).isEqualTo("user-a");
  }

  @Test
  void findByExternalIdReturnsEmptyForUnknownId() {
    assertThat(userRepository.findByExternalId("does-not-exist")).isEmpty();
  }

  @Test
  void findByUserNameReturnsPresentForKnownHandle() {
    userRepository.saveAndFlush(buildUser("sub-b", "ext-b", "user-b", "Bob"));

    final Optional<UserEntity> result = userRepository.findByUserName("user-b");

    assertThat(result).isPresent();
    assertThat(result.get().getSub()).isEqualTo("sub-b");
  }

  @Test
  void findByUserNameReturnsEmptyForUnknownHandle() {
    assertThat(userRepository.findByUserName("does-not-exist")).isEmpty();
  }

  @Test
  void externalIdUniqueConstraintIsEnforced() {
    userRepository.saveAndFlush(buildUser("sub-1", "id-1", "user-1", "U1"));

    assertThatThrownBy(
            () -> userRepository.saveAndFlush(buildUser("sub-2", "id-1", "user-2", "U2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void twoUsersWithNullExternalIdCoexist() {
    userRepository.saveAndFlush(buildUser("sub-x", null, "user-x", "X"));
    userRepository.saveAndFlush(buildUser("sub-y", null, "user-y", "Y"));

    assertThat(userRepository.findAll())
        .filteredOn(u -> !SystemUser.ID.equals(u.getId()))
        .filteredOn(u -> u.getExternalId() == null)
        .hasSize(2);
  }

  @Test
  void userNameNotNullConstraintIsEnforced() {
    final UserEntity user = new UserEntity();
    user.setSub("sub-no-username");
    user.setName("No Username");
    // userName intentionally not set
    assertThatThrownBy(() -> userRepository.saveAndFlush(user))
        .isInstanceOf(Exception.class);
  }

  @Test
  void userNameUniqueConstraintIsEnforced() {
    userRepository.saveAndFlush(buildUser("sub-a1", "ext-a1", "shared-handle", "A1"));

    assertThatThrownBy(
            () ->
                userRepository.saveAndFlush(
                    buildUser("sub-a2", "ext-a2", "shared-handle", "A2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void subUniqueConstraintIsUnchanged() {
    userRepository.saveAndFlush(buildUser("shared-sub", "ext-c1", "user-c1", "C1"));

    assertThatThrownBy(
            () ->
                userRepository.saveAndFlush(
                    buildUser("shared-sub", "ext-c2", "user-c2", "C2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void twoUsersWithNullSubCoexist() {
    // sub is now nullable — SCIM-pre-provisioned rows have no sub yet.
    userRepository.saveAndFlush(buildUser(null, "ext-no-sub-1", "user-ns1", "NS1"));
    userRepository.saveAndFlush(buildUser(null, "ext-no-sub-2", "user-ns2", "NS2"));

    assertThat(userRepository.findAll())
        .filteredOn(u -> !SystemUser.ID.equals(u.getId()))
        .filteredOn(u -> u.getSub() == null)
        .hasSize(2);
  }

  @Test
  void activeDefaultsToTrueOnFreshEntity() {
    final UserEntity persisted =
        userRepository.saveAndFlush(buildUser("sub-active", "ext-active", "user-active", "A"));

    assertThat(persisted.isActive()).isTrue();
  }

  @Test
  void activeCanBeFlippedToFalseAndBack() {
    final UserEntity persisted =
        userRepository.saveAndFlush(buildUser("sub-flip", "ext-flip", "user-flip", "F"));
    persisted.setActive(false);
    userRepository.saveAndFlush(persisted);
    assertThat(userRepository.findById(persisted.getId()).orElseThrow().isActive()).isFalse();

    persisted.setActive(true);
    userRepository.saveAndFlush(persisted);
    assertThat(userRepository.findById(persisted.getId()).orElseThrow().isActive()).isTrue();
  }
}
