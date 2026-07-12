package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.data.DbSchema;
import com.openelements.spring.base.services.apitoken.PrincipalDirectory;
import com.openelements.spring.base.services.apitoken.PrincipalDirectory.ResolvedPrincipal;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserEntityPrincipalDirectory;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link UserEntityPrincipalDirectory} — the bridge between
 * {@link PrincipalDirectory} (spec 010's port, temporarily owned by 012) and the
 * {@link UserEntity} data model.
 *
 * <h2>What is tested</h2>
 *
 * <p>Four behaviours of {@code resolveUser(subjectRef)} are pinned:
 *
 * <ol>
 *   <li><b>Lookup is by {@code externalId}, never by {@code sub}.</b> A row whose {@code sub}
 *       happens to equal the queried value but whose {@code externalId} does not must
 *       <em>not</em> resolve — the directory is a SCIM-side resolver, not a JWT-side one.
 *   <li><b>Inactive users are returned, not filtered.</b> The directory exposes the {@code
 *       active} flag verbatim and lets the introspector layer decide whether to allow the
 *       request. Filtering here would silently mask deactivations from the audit trail.
 *   <li><b>Unknown subjects return {@link Optional#empty()}</b> rather than throwing, so callers
 *       can use a plain {@code Optional} flow.
 *   <li><b>Null subject reference is rejected with NPE</b> — fail-fast on misuse.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * starts Postgres via Testcontainers. The full JPA stack is in play — {@link UserRepository}
 * and {@link UserEntityPrincipalDirectory} are real beans driven against a real database, so
 * the lookup-by-column behaviour is exercised verbatim. {@code @BeforeEach} clears all rows
 * except {@link SystemUser#ID} to keep each test isolated.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. Every collaborator (repository, directory, database) is
 * real — the test asserts the contract end-to-end.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserEntityPrincipalDirectory")
class UserEntityPrincipalDirectoryTest {

  @Autowired private UserEntityPrincipalDirectory directory;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

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

  private UserEntity persist(
      final String sub, final String externalId, final String userName, final String displayName) {
    return persist(sub, externalId, userName, displayName, true);
  }

  private UserEntity persist(
      final String sub,
      final String externalId,
      final String userName,
      final String displayName,
      final boolean active) {
    final UserEntity user = new UserEntity();
    user.setSub(sub);
    user.setExternalId(externalId);
    user.setUserName(userName);
    user.setName(displayName);
    user.setActive(active);
    return userRepository.saveAndFlush(user);
  }

  @Test
  @DisplayName("resolveUser(externalId) returns a ResolvedPrincipal carrying subjectRef, displayName, active=true, and empty roles/groups.")
  void resolvesActiveUserByExternalId() {
    persist("sub-alice", "alice-ext", "alice", "Alice");

    final Optional<ResolvedPrincipal> result = directory.resolveUser("alice-ext");

    assertThat(result).isPresent();
    final ResolvedPrincipal resolved = result.orElseThrow();
    assertThat(resolved.subjectRef()).isEqualTo("alice-ext");
    assertThat(resolved.active()).isTrue();
    assertThat(resolved.displayName()).isEqualTo("Alice");
    assertThat(resolved.roles()).isEmpty();
    assertThat(resolved.groups()).isEmpty();
  }

  /**
   * The directory deliberately does not filter inactive users — it surfaces the {@code active}
   * flag verbatim and lets the introspector layer decide whether the request proceeds.
   * Filtering here would mask deactivations from logs and audit trails.
   */
  @Test
  @DisplayName("resolveUser(...) returns the row for inactive users with active=false — filtering is the introspector's job, not the directory's.")
  void resolvesInactiveUserAsInactive() {
    persist("sub-bob", "bob-ext", "bob", "Bob", false);

    final Optional<ResolvedPrincipal> result = directory.resolveUser("bob-ext");

    assertThat(result).isPresent();
    // The directory does NOT filter inactive users — the introspector decides.
    assertThat(result.orElseThrow().active()).isFalse();
  }

  @Test
  @DisplayName("resolveUser(...) returns Optional.empty() for an unknown externalId rather than throwing.")
  void returnsEmptyForUnknownExternalId() {
    persist("sub-known", "known-ext", "known", "Known");

    assertThat(directory.resolveUser("ghost-ext")).isEmpty();
  }

  /**
   * Critical contract: a query whose value matches a row's {@code sub} but not its
   * {@code externalId} must miss. Some IdPs use different identifiers on the OIDC side (sub)
   * and the SCIM side (externalId); the directory is a SCIM-facing resolver, so it must use
   * the SCIM identifier exclusively. A regression here would silently cross-pollinate the
   * two identifier spaces.
   */
  @Test
  @DisplayName("Lookup matches on externalId only — a row whose sub equals the queried value but whose externalId does not must NOT resolve.")
  void lookupIsByExternalIdNotBySub() {
    // User has distinct sub and externalId — common for IdPs that use different identifiers.
    persist("S1", "X1", "user-s1-x1", "S1 user");

    // Lookup by sub returns nothing.
    assertThat(directory.resolveUser("S1")).isEmpty();
    // Lookup by externalId returns the row.
    assertThat(directory.resolveUser("X1")).isPresent();
  }

  @Test
  @DisplayName("resolveUser(null) throws NullPointerException — fail fast on caller misuse rather than issuing a null-keyed query.")
  void rejectsNullSubjectRef() {
    assertThatThrownBy(() -> directory.resolveUser(null))
        .isInstanceOf(NullPointerException.class);
  }
}
