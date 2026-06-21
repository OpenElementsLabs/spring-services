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

  @Test
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

    @Test
    void findBySubTakesPrecedenceOverExternalIdAndUserName() {
      setupAuth("S", "S-name", "s@example.com", null);
      final UserEntity rowA = createExistingUser("S", "S-name", "s@example.com", null);
      when(userRepository.findBySub("S")).thenReturn(Optional.of(rowA));

      final UserEntity result = userService.getCurrentUserEntity();

      assertThat(result).isSameAs(rowA);
      verify(userRepository, never()).findByExternalId(any());
      verify(userRepository, never()).findByUserName(any());
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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
