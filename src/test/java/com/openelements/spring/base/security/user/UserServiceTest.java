package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.image.ImageData;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserService")
class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuthService authService = mock(AuthService.class);
    private final UserService userService =
            new UserService(userRepository, eventPublisher, authService);

    private void setupAuth(final String sub, final String name, final String email) {
        when(authService.getUserInformation()).thenReturn(new UserInformation(sub, name, email));
    }

    private UserEntity createExistingUser(final String sub, final String name, final String email) {
        final UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setSub(sub);
        entity.setName(name);
        entity.setEmail(email);
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
            setupAuth("auth0|new", "New User", "new@example.com");
            when(userRepository.findBySub("auth0|new")).thenReturn(Optional.empty());
            when(userRepository.saveAndFlush(any(UserEntity.class)))
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
            verify(userRepository).saveAndFlush(any(UserEntity.class));
        }

        @Test
        void shouldReturnExistingUserWithoutSaving() {
            // GIVEN
            setupAuth("auth0|existing", "Same Name", "same@example.com");
            final UserEntity existing =
                    createExistingUser("auth0|existing", "Same Name", "same@example.com");
            when(userRepository.findBySub("auth0|existing")).thenReturn(Optional.of(existing));

            // WHEN
            final UserDto dto = userService.getCurrentUser();

            // THEN
            assertThat(dto.name()).isEqualTo("Same Name");
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void shouldUpdateExistingUserWhenNameChanged() {
            // GIVEN
            setupAuth("auth0|updated", "Updated Name", "same@example.com");
            final UserEntity existing =
                    createExistingUser("auth0|updated", "Old Name", "same@example.com");
            when(userRepository.findBySub("auth0|updated")).thenReturn(Optional.of(existing));
            when(userRepository.saveAndFlush(existing)).thenReturn(existing);

            // WHEN
            final UserDto dto = userService.getCurrentUser();

            // THEN
            assertThat(dto.name()).isEqualTo("Updated Name");
            verify(userRepository).saveAndFlush(existing);
        }

        @Test
        void shouldUpdateExistingUserWhenEmailChanged() {
            // GIVEN
            setupAuth("auth0|email", "Name", "new@example.com");
            final UserEntity existing = createExistingUser("auth0|email", "Name", "old@example.com");
            when(userRepository.findBySub("auth0|email")).thenReturn(Optional.of(existing));
            when(userRepository.saveAndFlush(existing)).thenReturn(existing);

            // WHEN
            final UserDto dto = userService.getCurrentUser();

            // THEN
            assertThat(dto.email()).isEqualTo("new@example.com");
            verify(userRepository).saveAndFlush(existing);
        }
    }

    @Nested
    @DisplayName("uploadAvatarForCurrentUser")
    class UploadAvatarTest {

        @Test
        void shouldUploadValidJpegAvatar() {
            // GIVEN
            setupAuth("auth0|avatar", "User", "user@example.com");
            final UserEntity existing = createExistingUser("auth0|avatar", "User", "user@example.com");
            when(userRepository.findBySub("auth0|avatar")).thenReturn(Optional.of(existing));
            when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
            final byte[] imageData = new byte[]{1, 2, 3, 4};

            // WHEN
            final ImageData data = ImageData.of(imageData, "image/jpeg");
            final UserDto dto = userService.updateAvatarForCurrentUser(data);

            // THEN
            assertThat(dto.hasAvatar()).isTrue();
        }

        @Test
        void shouldUploadValidPngAvatar() {
            // GIVEN
            setupAuth("auth0|avatar", "User", "user@example.com");
            final UserEntity existing = createExistingUser("auth0|avatar", "User", "user@example.com");
            when(userRepository.findBySub("auth0|avatar")).thenReturn(Optional.of(existing));
            when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

            // WHEN
            final UserDto dto = userService.updateAvatarForCurrentUser(ImageData.of(new byte[]{1}, "image/png"));

            // THEN
            assertThat(dto.hasAvatar()).isTrue();
        }

        @Test
        void shouldRejectNullData() {
            assertThatThrownBy(() -> userService.updateAvatarForCurrentUser(ImageData.of(null, "image/jpeg")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("data must not be null");
        }

        @Test
        void shouldRejectEmptyData() {
            assertThatThrownBy(() -> userService.updateAvatarForCurrentUser(ImageData.of(new byte[0], "image/jpeg")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectFileTooLarge() {
            // GIVEN
            final byte[] largeData = new byte[2 * 1024 * 1024 + 1]; // >2MB

            // WHEN & THEN
            assertThatThrownBy(() -> userService.updateAvatarForCurrentUser(ImageData.of(largeData, "image/jpeg")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectInvalidContentType() {
            assertThatThrownBy(() -> userService.updateAvatarForCurrentUser(ImageData.of(new byte[]{1}, "image/gif")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getAvatarOfCurrentUser")
    class GetAvatarTest {

        @Test
        void shouldReturnAvatarData() {
            // GIVEN
            setupAuth("auth0|avatar", "User", "user@example.com");
            final UserEntity existing = createExistingUser("auth0|avatar", "User", "user@example.com");
            existing.setAvatar(new byte[]{10, 20, 30});
            existing.setAvatarContentType("image/png");
            when(userRepository.findBySub("auth0|avatar")).thenReturn(Optional.of(existing));

            // WHEN
            final ImageData imageData = userService.getAvatarOfCurrentUser().orElse(null);

            // THEN
            assertThat(imageData.data()).isEqualTo(new byte[]{10, 20, 30});
            assertThat(imageData.imageType().getContentType()).isEqualTo("image/png");
        }
    }

    @Nested
    @DisplayName("deleteAvatarOfCurrentUser")
    class DeleteAvatarTest {

        @Test
        void shouldClearAvatarFields() {
            // GIVEN
            setupAuth("auth0|del", "User", "user@example.com");
            final UserEntity existing = createExistingUser("auth0|del", "User", "user@example.com");
            existing.setAvatar(new byte[]{1, 2});
            existing.setAvatarContentType("image/jpeg");
            when(userRepository.findBySub("auth0|del")).thenReturn(Optional.of(existing));
            when(userRepository.saveAndFlush(existing)).thenReturn(existing);

            // WHEN
            userService.deleteAvatarOfCurrentUser();

            // THEN
            assertThat(existing.getAvatar()).isNull();
            assertThat(existing.getAvatarContentType()).isNull();
            verify(userRepository).saveAndFlush(existing);
        }
    }
}
