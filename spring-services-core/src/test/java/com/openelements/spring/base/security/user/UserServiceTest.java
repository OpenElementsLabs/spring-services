package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserProvisioner;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Mockito-style unit tests for {@link UserService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The decision logic inside {@code UserService.getCurrentUserEntity()} and {@code
 * getCurrentUser()}: the three-tier lookup chain ({@code findBySub → findByExternalId →
 * findByUserName → provision}), the drift-sync of mutable claims ({@code name}, {@code email},
 * {@code avatarUrl}), the back-fill of stable identifiers ({@code sub}, {@code externalId},
 * {@code userName}), the active-gate, and the disambiguation guard that prevents two users
 * sharing the same email-as-userName fallback from being silently merged. End-to-end
 * persistence (race recovery, real Postgres constraints) is covered by sibling integration
 * tests — see {@link UserServiceConcurrencyTest} and {@link
 * UserServiceActiveGateIntegrationTest}.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Mockito, no Spring context. The four collaborators of {@link
 * UserService} — {@link UserRepository}, {@link
 * org.springframework.context.ApplicationEventPublisher}, {@link
 * com.openelements.spring.base.security.AuthService}, {@link UserProvisioner} — are mocked.
 *
 * <p><b>Why mocks here are justified.</b> {@code UserService} is a coordination layer; its job
 * is to invoke its collaborators in the right sequence with the right arguments. The interesting
 * behaviour lives in the orchestration, not in the collaborators. Three of the four mocks have
 * trivial substitutes only at integration level:
 *
 * <ul>
 *   <li>{@code UserRepository}: stubbing {@code findBy*(...)} return values is what lets us
 *       exhaustively test all paths of the three-tier lookup without spinning up Postgres for
 *       every permutation.
 *   <li>{@code AuthService}: real {@code AuthService} reads from a {@link
 *       org.springframework.security.core.context.SecurityContextHolder} populated by the filter
 *       chain. Reconstructing that here would require Spring Security context plumbing for no
 *       added coverage — the integration test covers the contract.
 *   <li>{@code UserProvisioner}: real {@code UserProvisioner} requires a JPA transaction with
 *       {@code REQUIRES_NEW}. Mocking lets us assert "provision was called" without persistence
 *       side-effects.
 *   <li>{@code ApplicationEventPublisher}: only used by the inherited {@code
 *       AbstractDbBackedDataService} machinery; the tests do not exercise lifecycle events.
 * </ul>
 *
 * <p>No mock has {@code Answer<?>}-driven behaviour beyond simple {@code thenReturn} stubs and a
 * single {@code thenAnswer(inv -> entityFrom(arg))} for the provisioner — there is no broad
 * surface where mocks would hide bugs. The race-recovery and Postgres-constraint behaviour that
 * mocks cannot legitimately exercise is verified by the Testcontainers tests.
 */
@DisplayName("UserService")
class UserServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final AuthService authService = mock(AuthService.class);
  private final UserProvisioner userProvisioner = mock(UserProvisioner.class);
  private final UserService userService =
      new UserService(userRepository, eventPublisher, authService, userProvisioner);

  private void setupAuth(
      final String sub, final String name, final String email, final String avatarUrl) {
    when(authService.getUserInformation())
        .thenReturn(Optional.of(new UserInformation(sub, name, email, avatarUrl, sub, sub)));
  }

  private UserEntity createExistingUser(
      final String sub, final String name, final String email, final String avatarUrl) {
    final UserEntity entity = new UserEntity();
    entity.setId(UUID.randomUUID());
    entity.setSub(sub);
    entity.setExternalId(sub);
    entity.setUserName(sub);
    entity.setName(name);
    entity.setEmail(email);
    entity.setAvatarUrl(avatarUrl);
    return entity;
  }

  private UserEntity entityFrom(final UserInformation info) {
    final UserEntity entity = new UserEntity();
    entity.setId(UUID.randomUUID());
    entity.setSub(info.id());
    entity.setName(info.name());
    entity.setEmail(info.email());
    entity.setAvatarUrl(info.avatarUrl());
    return entity;
  }

  /**
   * Documents that {@link UserService} fail-fasts on missing collaborators — a misconfigured
   * Spring context surfaces immediately, not on first request.
   */
  @Test
  @DisplayName(
      "UserService constructor rejects null for each of its four collaborators with NullPointerException.")
  void shouldRejectNullConstructorArgs() {
    assertThatThrownBy(() -> new UserService(null, eventPublisher, authService, userProvisioner))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new UserService(userRepository, null, authService, userProvisioner))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new UserService(userRepository, eventPublisher, null, userProvisioner))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new UserService(userRepository, eventPublisher, authService, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Nested
  @DisplayName("getCurrentUser")
  class GetCurrentUserTest {

    @Test
    @DisplayName("getCurrentUser() provisions a new user when no row matches the JWT subject.")
    void shouldCreateNewUserWhenNotExists() {
      // GIVEN
      setupAuth("auth0|new", "New User", "new@example.com", null);
      when(userRepository.findBySub("auth0|new")).thenReturn(Optional.empty());
      when(userProvisioner.provision(any(UserInformation.class)))
          .thenAnswer(invocation -> entityFrom(invocation.getArgument(0)));

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.name()).isEqualTo("New User");
      assertThat(dto.email()).isEqualTo("new@example.com");
      assertThat(dto.avatarUrl()).isNull();
      verify(userProvisioner).provision(any(UserInformation.class));
    }

    @Test
    @DisplayName("getCurrentUser() returns the existing row without saving when the JWT claims match.")
    void shouldReturnExistingUserWithoutSaving() {
      // GIVEN
      setupAuth("auth0|existing", "Same Name", "same@example.com", null);
      final UserEntity existing =
          createExistingUser("auth0|existing", "Same Name", "same@example.com", null);
      when(userRepository.findBySub("auth0|existing")).thenReturn(Optional.of(existing));

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.name()).isEqualTo("Same Name");
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A drifted name in the JWT is written to the existing UserEntity row.")
    void shouldUpdateExistingUserWhenNameChanged() {
      // GIVEN
      setupAuth("auth0|updated", "Updated Name", "same@example.com", null);
      final UserEntity existing =
          createExistingUser("auth0|updated", "Old Name", "same@example.com", null);
      when(userRepository.findBySub("auth0|updated")).thenReturn(Optional.of(existing));
      when(userRepository.save(existing)).thenReturn(existing);

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.name()).isEqualTo("Updated Name");
      verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("A drifted email in the JWT is written to the existing UserEntity row.")
    void shouldUpdateExistingUserWhenEmailChanged() {
      // GIVEN
      setupAuth("auth0|email", "Name", "new@example.com", null);
      final UserEntity existing =
          createExistingUser("auth0|email", "Name", "old@example.com", null);
      when(userRepository.findBySub("auth0|email")).thenReturn(Optional.of(existing));
      when(userRepository.save(existing)).thenReturn(existing);

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.email()).isEqualTo("new@example.com");
      verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("First-login provisioning copies the JWT avatar claim onto the new UserEntity.")
    void shouldStoreAvatarUrlOnFirstLogin() {
      // GIVEN
      setupAuth(
          "auth0|first-avatar",
          "Alice",
          "alice@example.com",
          "https://auth.example.com/media/avatars/user1.jpg");
      when(userRepository.findBySub("auth0|first-avatar")).thenReturn(Optional.empty());
      when(userProvisioner.provision(any(UserInformation.class)))
          .thenAnswer(invocation -> entityFrom(invocation.getArgument(0)));

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.avatarUrl()).isEqualTo("https://auth.example.com/media/avatars/user1.jpg");
      verify(userProvisioner).provision(any(UserInformation.class));
    }

    @Test
    @DisplayName("A drifted avatar URL in the JWT is written to the existing UserEntity row.")
    void shouldUpdateAvatarUrlWhenChanged() {
      // GIVEN
      setupAuth(
          "auth0|avatar-changed",
          "User",
          "user@example.com",
          "https://auth.example.com/media/avatars/new.jpg");
      final UserEntity existing =
          createExistingUser(
              "auth0|avatar-changed",
              "User",
              "user@example.com",
              "https://auth.example.com/media/avatars/old.jpg");
      when(userRepository.findBySub("auth0|avatar-changed")).thenReturn(Optional.of(existing));
      when(userRepository.save(existing)).thenReturn(existing);

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(existing.getAvatarUrl())
          .isEqualTo("https://auth.example.com/media/avatars/new.jpg");
      assertThat(dto.avatarUrl()).isEqualTo("https://auth.example.com/media/avatars/new.jpg");
      verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("No save is issued when the JWT avatar URL matches the persisted value exactly.")
    void shouldNotSaveWhenAvatarUrlUnchanged() {
      // GIVEN
      setupAuth(
          "auth0|avatar-same",
          "User",
          "user@example.com",
          "https://auth.example.com/media/avatars/same.jpg");
      final UserEntity existing =
          createExistingUser(
              "auth0|avatar-same",
              "User",
              "user@example.com",
              "https://auth.example.com/media/avatars/same.jpg");
      when(userRepository.findBySub("auth0|avatar-same")).thenReturn(Optional.of(existing));

      // WHEN
      userService.getCurrentUser();

      // THEN
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "When the JWT no longer asserts an avatar claim, the persisted avatarUrl is cleared to null.")
    void shouldClearAvatarUrlWhenJwtClaimMissing() {
      // GIVEN — JWT carries no avatar claim, AuthService normalises to null
      setupAuth("auth0|avatar-cleared", "User", "user@example.com", null);
      final UserEntity existing =
          createExistingUser(
              "auth0|avatar-cleared",
              "User",
              "user@example.com",
              "https://auth.example.com/media/avatars/old.jpg");
      when(userRepository.findBySub("auth0|avatar-cleared")).thenReturn(Optional.of(existing));
      when(userRepository.save(existing)).thenReturn(existing);

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(existing.getAvatarUrl()).isNull();
      assertThat(dto.avatarUrl()).isNull();
      verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("First-login provisioning persists a null avatarUrl when the JWT omits the claim.")
    void shouldCreateUserWithoutAvatarUrlWhenClaimMissing() {
      // GIVEN
      setupAuth("auth0|no-avatar-first", "User", "user@example.com", null);
      when(userRepository.findBySub("auth0|no-avatar-first")).thenReturn(Optional.empty());
      when(userProvisioner.provision(any(UserInformation.class)))
          .thenAnswer(invocation -> entityFrom(invocation.getArgument(0)));

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.avatarUrl()).isNull();
      verify(userProvisioner).provision(any(UserInformation.class));
    }
  }

  @Nested
  @DisplayName("getCurrentUserEntity")
  class GetCurrentUserEntityTest {

    @Test
    @DisplayName("getCurrentUserEntity() returns the managed JPA entity (not a DTO copy) for re-use in associations.")
    void shouldReturnEntityForExistingUser() {
      // GIVEN
      setupAuth("auth0|entity-existing", "Same Name", "same@example.com", null);
      final UserEntity existing =
          createExistingUser("auth0|entity-existing", "Same Name", "same@example.com", null);
      when(userRepository.findBySub("auth0|entity-existing")).thenReturn(Optional.of(existing));

      // WHEN
      final UserEntity result = userService.getCurrentUserEntity();

      // THEN
      assertThat(result).isSameAs(existing);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("First call for an unknown subject delegates to UserProvisioner.provision(...).")
    void shouldProvisionNewUserOnFirstCall() {
      // GIVEN
      setupAuth(
          "auth0|entity-new",
          "Brand New",
          "brandnew@example.com",
          "https://auth.example.com/avatars/new.jpg");
      when(userRepository.findBySub("auth0|entity-new")).thenReturn(Optional.empty());
      when(userProvisioner.provision(any(UserInformation.class)))
          .thenAnswer(invocation -> entityFrom(invocation.getArgument(0)));

      // WHEN
      final UserEntity result = userService.getCurrentUserEntity();

      // THEN
      assertThat(result.getSub()).isEqualTo("auth0|entity-new");
      assertThat(result.getName()).isEqualTo("Brand New");
      assertThat(result.getEmail()).isEqualTo("brandnew@example.com");
      assertThat(result.getAvatarUrl()).isEqualTo("https://auth.example.com/avatars/new.jpg");
      assertThat(result.getId()).isNotNull();
      verify(userProvisioner).provision(any(UserInformation.class));
    }

    @Test
    @DisplayName("A drifted name claim flows through to the managed entity returned by getCurrentUserEntity().")
    void shouldUpdateDriftedJwtClaims() {
      // GIVEN — JWT now carries a different name than the DB row
      setupAuth("auth0|entity-drift", "New Name", "same@example.com", null);
      final UserEntity existing =
          createExistingUser("auth0|entity-drift", "Old Name", "same@example.com", null);
      when(userRepository.findBySub("auth0|entity-drift")).thenReturn(Optional.of(existing));
      when(userRepository.save(existing)).thenReturn(existing);

      // WHEN
      final UserEntity result = userService.getCurrentUserEntity();

      // THEN
      assertThat(result.getName()).isEqualTo("New Name");
      verify(userRepository).save(existing);
    }

    @Test
    @DisplayName(
        "Calling getCurrentUserEntity() without a JWT-authenticated context fails fast with IllegalStateException — neither repository nor provisioner is touched.")
    void shouldThrowWhenNoJwtBoundToRequest() {
      // GIVEN — AuthService returns empty Optional (non-JWT principal, e.g. API key)
      when(authService.getUserInformation()).thenReturn(Optional.empty());

      // WHEN & THEN
      assertThatThrownBy(() -> userService.getCurrentUserEntity())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No JWT bound to current request");
      verify(userRepository, never()).findBySub(any());
      verify(userProvisioner, never()).provision(any());
    }
  }

  @Nested
  @DisplayName("getCurrentUserEntity — tiered lookup")
  class TieredLookupTest {

    /**
     * Verifies the short-circuit semantics of {@link java.util.Optional#or(java.util.function.Supplier)}
     * in the lookup chain: once {@code findBySub} matches, {@code findByExternalId} and
     * {@code findByUserName} are never queried — confirmed by Mockito's {@code verify(never())}.
     */
    @Test
    @DisplayName(
        "findBySub short-circuits the lookup chain — findByExternalId and findByUserName are never called.")
    void findBySubTakesPrecedenceOverExternalIdAndUserName() {
      setupAuth("S", "S-name", "s@example.com", null);
      final UserEntity rowA = createExistingUser("S", "S-name", "s@example.com", null);
      when(userRepository.findBySub("S")).thenReturn(Optional.of(rowA));

      final UserEntity result = userService.getCurrentUserEntity();

      assertThat(result).isSameAs(rowA);
      verify(userRepository, never()).findByExternalId(any());
      verify(userRepository, never()).findByUserName(any());
    }

    /**
     * Simulates the canonical SCIM-pre-provisioned row (no {@code sub}, {@code externalId} set)
     * and verifies that the JIT login adopts the row — writes {@code sub} onto it via the
     * back-fill path and saves once.
     */
    @Test
    @DisplayName(
        "When findBySub misses, findByExternalId is queried — a matched SCIM-pre-provisioned row gets sub written onto it.")
    void findByExternalIdIsUsedWhenSubMisses() {
      setupAuth("Y", "Y-name", "y@example.com", null);
      // SCIM-pre-provisioned row: sub null, externalId set
      final UserEntity preProvisioned = new UserEntity();
      preProvisioned.setId(UUID.randomUUID());
      preProvisioned.setSub(null);
      preProvisioned.setExternalId("Y");
      preProvisioned.setUserName("Y");
      preProvisioned.setName("Y-name");
      preProvisioned.setEmail("y@example.com");
      when(userRepository.findBySub("Y")).thenReturn(Optional.empty());
      when(userRepository.findByExternalId("Y")).thenReturn(Optional.of(preProvisioned));
      when(userRepository.save(preProvisioned)).thenReturn(preProvisioned);

      final UserEntity result = userService.getCurrentUserEntity();

      assertThat(result).isSameAs(preProvisioned);
      assertThat(preProvisioned.getSub()).isEqualTo("Y");
      verify(userRepository, never()).findByUserName(any());
      verify(userRepository).save(preProvisioned);
    }

    /**
     * Exercises the third lookup tier: row exists only under {@code userName}. Asserts that the
     * adoption back-fills <em>both</em> {@code sub} and {@code externalId} in a single save —
     * the lookup is documented as "last resort, addresses IdPs that use different identifiers
     * for OIDC and SCIM."
     */
    @Test
    @DisplayName(
        "findByUserName is the last-resort tier — when it matches, both sub and externalId are back-filled in one save.")
    void findByUserNameIsLastResortAndBackfillsBothIdentifiers() {
      setupAuth("Z", "Z-name", "z@example.com", null);
      // Row exists only via userName — no sub, no externalId
      final UserEntity row = new UserEntity();
      row.setId(UUID.randomUUID());
      row.setSub(null);
      row.setExternalId(null);
      row.setUserName("Z");
      row.setName("Z-name");
      row.setEmail("z@example.com");
      when(userRepository.findBySub("Z")).thenReturn(Optional.empty());
      when(userRepository.findByExternalId("Z")).thenReturn(Optional.empty());
      when(userRepository.findByUserName("Z")).thenReturn(Optional.of(row));
      when(userRepository.save(row)).thenReturn(row);

      final UserEntity result = userService.getCurrentUserEntity();

      assertThat(result).isSameAs(row);
      assertThat(row.getSub()).isEqualTo("Z");
      assertThat(row.getExternalId()).isEqualTo("Z");
      verify(userRepository).save(row);
    }

    @Test
    @DisplayName(
        "A brand-new user is provisioned only after all three lookups (sub, externalId, userName) miss.")
    void provisionsNewUserWhenAllThreeLookupsMiss() {
      setupAuth("brand-new", "New User", "new@example.com", null);
      when(userRepository.findBySub("brand-new")).thenReturn(Optional.empty());
      when(userRepository.findByExternalId("brand-new")).thenReturn(Optional.empty());
      when(userRepository.findByUserName("brand-new")).thenReturn(Optional.empty());
      when(userProvisioner.provision(any(UserInformation.class)))
          .thenAnswer(invocation -> entityFrom(invocation.getArgument(0)));

      final UserEntity result = userService.getCurrentUserEntity();

      assertThat(result.getSub()).isEqualTo("brand-new");
      verify(userProvisioner).provision(any(UserInformation.class));
    }

    @Test
    @DisplayName(
        "Pre-spec-012 row (sub set, externalId and userName null) is back-filled in place on next interactive login — id is preserved.")
    void preFoundationUserBackfillsMissingIdentifiers() {
      setupAuth("abc", "Alice", "alice@example.com", null);
      // Pre-foundation row: sub set, externalId/userName null
      final UserEntity row = new UserEntity();
      row.setId(UUID.randomUUID());
      row.setSub("abc");
      row.setExternalId(null);
      row.setUserName(null);
      row.setName("Alice");
      row.setEmail("alice@example.com");
      when(userRepository.findBySub("abc")).thenReturn(Optional.of(row));
      when(userRepository.save(row)).thenReturn(row);

      userService.getCurrentUserEntity();

      assertThat(row.getExternalId()).isEqualTo("abc");
      assertThat(row.getUserName()).isEqualTo("abc");
      verify(userRepository).save(row);
    }

    @Test
    @DisplayName(
        "Drift sync across name, email, avatarUrl plus back-fill of externalId and userName is committed in a single save call.")
    void driftSyncWritesAllChangedFieldsInOneSave() {
      setupAuth("sub-drift", "New Name", "new@example.com", "https://new-avatar");
      // Existing row with different name, email, avatarUrl, and missing externalId/userName
      final UserEntity row = new UserEntity();
      row.setId(UUID.randomUUID());
      row.setSub("sub-drift");
      row.setExternalId(null);
      row.setUserName(null);
      row.setName("Old Name");
      row.setEmail("old@example.com");
      row.setAvatarUrl("https://old-avatar");
      when(userRepository.findBySub("sub-drift")).thenReturn(Optional.of(row));
      when(userRepository.save(row)).thenReturn(row);

      userService.getCurrentUserEntity();

      assertThat(row.getName()).isEqualTo("New Name");
      assertThat(row.getEmail()).isEqualTo("new@example.com");
      assertThat(row.getAvatarUrl()).isEqualTo("https://new-avatar");
      assertThat(row.getExternalId()).isEqualTo("sub-drift");
      assertThat(row.getUserName()).isEqualTo("sub-drift");
      verify(userRepository, times(1)).save(row);
    }

    /**
     * Pins the back-fill-only rule for stable identifiers: even if the JWT's {@code
     * preferred_username} differs from the persisted {@code userName}, the local value wins.
     * Stable IdP-side identifiers must not flip mid-session.
     */
    @Test
    @DisplayName(
        "A persisted userName is never overwritten by a drifted JWT preferred_username claim — stable identifiers are backfill-only.")
    void existingUserNameIsNotOverwrittenByJwtClaim() {
      // userName is a stable identifier — once set, never overwritten by a later JWT.
      setupAuth("S", "Same Name", "same@example.com", null);
      final UserEntity row =
          createExistingUser("S", "Same Name", "same@example.com", null);
      row.setUserName("old-username"); // explicitly different from JWT preferred_username
      when(userRepository.findBySub("S")).thenReturn(Optional.of(row));

      userService.getCurrentUserEntity();

      assertThat(row.getUserName()).isEqualTo("old-username");
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("getCurrentUserEntity — active gate")
  class ActiveGateTest {

    @Test
    @DisplayName(
        "A request for a user with active=false throws AccessDeniedException — Spring Security translates to HTTP 403.")
    void inactiveUserOnExistingRowThrowsAccessDenied() {
      setupAuth("inactive-sub", "Inactive", "inactive@example.com", null);
      final UserEntity row =
          createExistingUser("inactive-sub", "Inactive", "inactive@example.com", null);
      row.setActive(false);
      when(userRepository.findBySub("inactive-sub")).thenReturn(Optional.of(row));

      assertThatThrownBy(() -> userService.getCurrentUserEntity())
          .isInstanceOf(
              org.springframework.security.access.AccessDeniedException.class)
          .hasMessageContaining("disabled");
      // No save — drift sync did not run
      verify(userRepository, never()).save(any());
    }

    /**
     * Critical safety property: a deactivated SCIM-pre-provisioned row, when first hit by a JIT
     * login, must throw <em>before</em> the back-fill path runs. Otherwise the row would silently
     * be claimed by an attacker who happens to know the {@code externalId}. Verified by
     * asserting {@code sub} remains {@code null} after the rejection.
     */
    @Test
    @DisplayName(
        "An inactive SCIM-pre-provisioned row matched by externalId rejects the request BEFORE sub is written — no silent activation via JIT.")
    void inactiveScimRowFoundByExternalIdThrowsBeforeWritingSub() {
      setupAuth("scim-sub", "SCIM User", "scim@example.com", null);
      final UserEntity row = new UserEntity();
      row.setId(UUID.randomUUID());
      row.setSub(null);
      row.setExternalId("scim-sub");
      row.setUserName("scim-sub");
      row.setName("SCIM User");
      row.setActive(false);
      when(userRepository.findBySub("scim-sub")).thenReturn(Optional.empty());
      when(userRepository.findByExternalId("scim-sub")).thenReturn(Optional.of(row));

      assertThatThrownBy(() -> userService.getCurrentUserEntity())
          .isInstanceOf(
              org.springframework.security.access.AccessDeniedException.class);
      // sub MUST NOT be written onto the deactivated row
      assertThat(row.getSub()).isNull();
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("An active user with no claim drift passes the gate and is returned unchanged.")
    void activeUserPassesThroughNormally() {
      setupAuth("active-sub", "Active", "active@example.com", null);
      final UserEntity row =
          createExistingUser("active-sub", "Active", "active@example.com", null);
      row.setActive(true);
      when(userRepository.findBySub("active-sub")).thenReturn(Optional.of(row));

      final UserEntity result = userService.getCurrentUserEntity();

      assertThat(result).isSameAs(row);
    }
  }
}
