package com.openelements.spring.base.security.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link UserDto#fromEntity(UserEntity)} mapper.
 *
 * <h2>What is tested</h2>
 *
 * <p>The straight-through copy of {@code id}, {@code name}, {@code email}, and {@code avatarUrl}
 * from {@link UserEntity} to {@link UserDto}, including the nullable contract for the optional
 * fields — a {@code UserEntity} with no email and no avatar URL must produce a {@code UserDto}
 * with {@code null} for both, not a default string. This is the boundary that controllers and
 * SCIM provisioners cross when serialising users to JSON, so a silent change here would alter
 * the wire format.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests. Real {@link UserEntity} instances are populated via their setters
 * and passed straight to {@code UserDto.fromEntity(...)}; the resulting DTO is asserted
 * field-by-field.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. The mapper is a pure function over POJOs — there is nothing
 * to mock and nothing to stub.
 */
@DisplayName("UserDto")
class UserDtoTest {

  @Test
  @DisplayName("fromEntity(...) copies id, name, email and avatarUrl from a fully populated UserEntity onto the UserDto.")
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
  @DisplayName("A UserEntity with null email and null avatarUrl produces a UserDto with null for both — no defaults are invented.")
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
