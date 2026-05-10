package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
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
  private final UserService userService =
      new UserService(userRepository, eventPublisher, authService);

  private void setupAuth(
      final String sub, final String name, final String email, final String avatarUrl) {
    when(authService.getUserInformation())
        .thenReturn(new UserInformation(sub, name, email, avatarUrl));
  }

  private UserEntity createExistingUser(
      final String sub, final String name, final String email, final String avatarUrl) {
    final UserEntity entity = new UserEntity();
    entity.setId(UUID.randomUUID());
    entity.setSub(sub);
    entity.setName(name);
    entity.setEmail(email);
    entity.setAvatarUrl(avatarUrl);
    return entity;
  }

  @Test
  void shouldRejectNullConstructorArgs() {
    assertThatThrownBy(() -> new UserService(null, eventPublisher, authService))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new UserService(userRepository, null, authService))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new UserService(userRepository, eventPublisher, null))
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
      when(userRepository.save(any(UserEntity.class)))
          .thenAnswer(
              invocation -> {
                final UserEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
              });

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.name()).isEqualTo("New User");
      assertThat(dto.email()).isEqualTo("new@example.com");
      assertThat(dto.avatarUrl()).isNull();
      verify(userRepository).save(any(UserEntity.class));
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
      when(userRepository.save(any(UserEntity.class)))
          .thenAnswer(
              invocation -> {
                final UserEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
              });

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.avatarUrl()).isEqualTo("https://auth.example.com/media/avatars/user1.jpg");
      verify(userRepository).save(any(UserEntity.class));
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
      when(userRepository.save(any(UserEntity.class)))
          .thenAnswer(
              invocation -> {
                final UserEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
              });

      // WHEN
      final UserDto dto = userService.getCurrentUser();

      // THEN
      assertThat(dto.avatarUrl()).isNull();
      verify(userRepository).save(any(UserEntity.class));
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
      when(userRepository.save(any(UserEntity.class)))
          .thenAnswer(
              invocation -> {
                final UserEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
              });

      // WHEN
      final UserEntity result = userService.getCurrentUserEntity();

      // THEN
      assertThat(result.getSub()).isEqualTo("auth0|entity-new");
      assertThat(result.getName()).isEqualTo("Brand New");
      assertThat(result.getEmail()).isEqualTo("brandnew@example.com");
      assertThat(result.getAvatarUrl()).isEqualTo("https://auth.example.com/avatars/new.jpg");
      assertThat(result.getId()).isNotNull();
      verify(userRepository).save(any(UserEntity.class));
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
  }
}
