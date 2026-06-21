package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Schema-level integration tests covering the new {@code external_id}, {@code user_name}, and
 * {@code active} columns plus the relaxed {@code sub} constraint introduced by spec 012.
 *
 * <h2>What is tested</h2>
 *
 * <p>Five categories of schema contract are pinned against Postgres:
 *
 * <ol>
 *   <li><b>Finder methods.</b> {@code findByExternalId} and {@code findByUserName} return
 *       {@code Optional.of(row)} on hit and {@code Optional.empty()} on miss — the JPA derived
 *       query methods must actually be wired through.
 *   <li><b>Unique indexes on {@code external_id} and {@code user_name}.</b> A duplicate insert
 *       on either column raises {@link DataIntegrityViolationException}, surfaced from
 *       Postgres's unique-index violation. {@code UserService}'s race-recovery path depends on
 *       these violations being thrown.
 *   <li><b>{@code sub} unique constraint is unchanged.</b> Spec 012 relaxed the column to
 *       nullable but kept its uniqueness — two rows sharing a non-null {@code sub} must still
 *       collide.
 *   <li><b>Nullable {@code sub} and {@code external_id}.</b> Multiple rows with {@code null}
 *       on either column must coexist — SCIM-pre-provisioned rows have no {@code sub} yet, and
 *       legacy rows may have no {@code external_id}.
 *   <li><b>{@code user_name NOT NULL} plus {@code active} default.</b> Every row must have a
 *       {@code user_name} (no fallback at the schema level); {@code active} defaults to
 *       {@code true} and round-trips through {@code false} cleanly.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * starts Postgres via Testcontainers. Real {@link UserRepository} is autowired and exercised
 * through {@code saveAndFlush} — the {@code Flush} part is what makes the unique-index
 * violations surface synchronously inside the test method (without it, the exception would be
 * deferred to commit time and would not be assertable here). {@code @BeforeEach} clears all
 * rows except {@link SystemUser#ID}.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. The whole point of a schema test is to verify the actual
 * Postgres DDL behaves as expected — any mock would defeat the purpose.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserRepository — spec 012 schema")
class UserRepositorySchemaTest {

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

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
  @DisplayName("findByExternalId(known) returns the persisted row.")
  void findByExternalIdReturnsPresentForKnownId() {
    userRepository.saveAndFlush(buildUser("sub-a", "ext-a", "user-a", "Alice"));

    final Optional<UserEntity> result = userRepository.findByExternalId("ext-a");

    assertThat(result).isPresent();
    assertThat(result.get().getUserName()).isEqualTo("user-a");
  }

  @Test
  @DisplayName("findByExternalId(unknown) returns Optional.empty().")
  void findByExternalIdReturnsEmptyForUnknownId() {
    assertThat(userRepository.findByExternalId("does-not-exist")).isEmpty();
  }

  @Test
  @DisplayName("findByUserName(known) returns the persisted row.")
  void findByUserNameReturnsPresentForKnownHandle() {
    userRepository.saveAndFlush(buildUser("sub-b", "ext-b", "user-b", "Bob"));

    final Optional<UserEntity> result = userRepository.findByUserName("user-b");

    assertThat(result).isPresent();
    assertThat(result.get().getSub()).isEqualTo("sub-b");
  }

  @Test
  @DisplayName("findByUserName(unknown) returns Optional.empty().")
  void findByUserNameReturnsEmptyForUnknownHandle() {
    assertThat(userRepository.findByUserName("does-not-exist")).isEmpty();
  }

  /**
   * Pins the unique index on {@code external_id}: a second insert with the same value raises
   * {@link DataIntegrityViolationException} on flush. {@code UserService}'s race-recovery path
   * relies on this exception being thrown synchronously.
   */
  @Test
  @DisplayName("A duplicate external_id raises DataIntegrityViolationException on flush — unique index is enforced by Postgres.")
  void externalIdUniqueConstraintIsEnforced() {
    userRepository.saveAndFlush(buildUser("sub-1", "id-1", "user-1", "U1"));

    assertThatThrownBy(
            () -> userRepository.saveAndFlush(buildUser("sub-2", "id-1", "user-2", "U2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * Documents that the unique index on {@code external_id} does <em>not</em> apply to {@code
   * NULL} values — multiple rows without a SCIM identifier must coexist. Postgres treats {@code
   * NULL} as distinct from {@code NULL} for unique-index purposes, which is the desired
   * behaviour here (legacy rows have no external_id yet).
   */
  @Test
  @DisplayName("Multiple rows with null external_id coexist — the unique index treats NULLs as distinct (Postgres default).")
  void twoUsersWithNullExternalIdCoexist() {
    userRepository.saveAndFlush(buildUser("sub-x", null, "user-x", "X"));
    userRepository.saveAndFlush(buildUser("sub-y", null, "user-y", "Y"));

    assertThat(userRepository.findAll())
        .filteredOn(u -> !SystemUser.ID.equals(u.getId()))
        .filteredOn(u -> u.getExternalId() == null)
        .hasSize(2);
  }

  @Test
  @DisplayName("Saving a row with a null user_name fails — the column is NOT NULL at the schema level.")
  void userNameNotNullConstraintIsEnforced() {
    final UserEntity user = new UserEntity();
    user.setSub("sub-no-username");
    user.setName("No Username");
    // userName intentionally not set
    assertThatThrownBy(() -> userRepository.saveAndFlush(user))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("A duplicate user_name raises DataIntegrityViolationException — the column carries a unique index.")
  void userNameUniqueConstraintIsEnforced() {
    userRepository.saveAndFlush(buildUser("sub-a1", "ext-a1", "shared-handle", "A1"));

    assertThatThrownBy(
            () ->
                userRepository.saveAndFlush(
                    buildUser("sub-a2", "ext-a2", "shared-handle", "A2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * Spec 012 relaxed {@code sub} to nullable but kept its unique constraint — regression guard
   * against accidentally widening the column to allow duplicates as part of the same migration.
   */
  @Test
  @DisplayName("A duplicate non-null sub still raises DataIntegrityViolationException — spec 012 kept the unique constraint.")
  void subUniqueConstraintIsUnchanged() {
    userRepository.saveAndFlush(buildUser("shared-sub", "ext-c1", "user-c1", "C1"));

    assertThatThrownBy(
            () ->
                userRepository.saveAndFlush(
                    buildUser("shared-sub", "ext-c2", "user-c2", "C2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * Symmetric to the null-{@code external_id} case: SCIM-pre-provisioned rows have no
   * {@code sub} yet, so multiple null-{@code sub} rows must coexist. Pins the desired Postgres
   * behaviour that {@code NULL} is distinct from {@code NULL} for the unique index.
   */
  @Test
  @DisplayName("Multiple rows with null sub coexist — SCIM-pre-provisioned rows have no sub yet, and the unique index treats NULLs as distinct.")
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
  @DisplayName("A freshly saved UserEntity has active=true — the column's default applied.")
  void activeDefaultsToTrueOnFreshEntity() {
    final UserEntity persisted =
        userRepository.saveAndFlush(buildUser("sub-active", "ext-active", "user-active", "A"));

    assertThat(persisted.isActive()).isTrue();
  }

  @Test
  @DisplayName("The active flag round-trips through false and back to true — toggling persists and reloads correctly.")
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
