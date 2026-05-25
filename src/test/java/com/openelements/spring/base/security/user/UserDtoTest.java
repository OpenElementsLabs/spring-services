package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserDto")
class UserDtoTest {

  @Test
  void shouldConvertFromEntityWithAvatarUrl() {
    // GIVEN
    final UUID id = UUID.randomUUID();
    final UserEntity entity = new UserEntity();
    entity.setId(id);
    entity.setSub("auth0|123");
    entity.setName("John Doe");
    entity.setEmail("john@example.com");
    entity.setAvatarUrl("https://auth.example.com/avatars/john.jpg");

    // WHEN
    final UserDto dto = UserDto.fromEntity(entity);

    // THEN
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.name()).isEqualTo("John Doe");
    assertThat(dto.email()).isEqualTo("john@example.com");
    assertThat(dto.avatarUrl()).isEqualTo("https://auth.example.com/avatars/john.jpg");
  }

  @Test
  void shouldConvertFromEntityWithoutAvatarUrl() {
    // GIVEN
    final UserEntity entity = new UserEntity();
    entity.setId(UUID.randomUUID());
    entity.setSub("auth0|456");
    entity.setName("Jane Doe");

    // WHEN
    final UserDto dto = UserDto.fromEntity(entity);

    // THEN
    assertThat(dto.avatarUrl()).isNull();
    assertThat(dto.email()).isNull();
  }
}
