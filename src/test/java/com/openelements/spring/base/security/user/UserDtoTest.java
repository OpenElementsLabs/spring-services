package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserDto")
class UserDtoTest {

  @Test
  void shouldConvertFromEntityWithAvatar() {
    // GIVEN
    final UUID id = UUID.randomUUID();
    final UserEntity entity = new UserEntity();
    entity.setId(id);
    entity.setSub("auth0|123");
    entity.setName("John Doe");
    entity.setEmail("john@example.com");
    entity.setAvatar(new byte[] {1, 2, 3});
    entity.setAvatarContentType("image/png");

    // WHEN
    final UserDto dto = UserDto.fromEntity(entity);

    // THEN
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.name()).isEqualTo("John Doe");
    assertThat(dto.email()).isEqualTo("john@example.com");
    assertThat(dto.hasAvatar()).isTrue();
  }

  @Test
  void shouldConvertFromEntityWithoutAvatar() {
    // GIVEN
    final UserEntity entity = new UserEntity();
    entity.setId(UUID.randomUUID());
    entity.setSub("auth0|456");
    entity.setName("Jane Doe");

    // WHEN
    final UserDto dto = UserDto.fromEntity(entity);

    // THEN
    assertThat(dto.hasAvatar()).isFalse();
    assertThat(dto.email()).isNull();
  }
}
