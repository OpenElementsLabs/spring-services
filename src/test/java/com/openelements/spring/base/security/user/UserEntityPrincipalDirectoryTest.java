package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.services.apitoken.PrincipalDirectory;
import com.openelements.spring.base.services.apitoken.PrincipalDirectory.ResolvedPrincipal;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserEntityPrincipalDirectory;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link UserEntityPrincipalDirectory} against a real Postgres database.
 * The bridge between {@link PrincipalDirectory} (spec 010's port, temporarily owned by 012) and
 * the {@link UserEntity} data model.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("UserEntityPrincipalDirectory")
class UserEntityPrincipalDirectoryTest {

  @Autowired private UserEntityPrincipalDirectory directory;

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void cleanUsers() {
    final List<UserEntity> toDelete =
        userRepository.findAll().stream()
            .filter(u -> !SystemUser.ID.equals(u.getId()))
            .toList();
    userRepository.deleteAll(toDelete);
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

  @Test
  void resolvesInactiveUserAsInactive() {
    persist("sub-bob", "bob-ext", "bob", "Bob", false);

    final Optional<ResolvedPrincipal> result = directory.resolveUser("bob-ext");

    assertThat(result).isPresent();
    // The directory does NOT filter inactive users — the introspector decides.
    assertThat(result.orElseThrow().active()).isFalse();
  }

  @Test
  void returnsEmptyForUnknownExternalId() {
    persist("sub-known", "known-ext", "known", "Known");

    assertThat(directory.resolveUser("ghost-ext")).isEmpty();
  }

  @Test
  void lookupIsByExternalIdNotBySub() {
    // User has distinct sub and externalId — common for IdPs that use different identifiers.
    persist("S1", "X1", "user-s1-x1", "S1 user");

    // Lookup by sub returns nothing.
    assertThat(directory.resolveUser("S1")).isEmpty();
    // Lookup by externalId returns the row.
    assertThat(directory.resolveUser("X1")).isPresent();
  }

  @Test
  void rejectsNullSubjectRef() {
    assertThatThrownBy(() -> directory.resolveUser(null))
        .isInstanceOf(NullPointerException.class);
  }
}
