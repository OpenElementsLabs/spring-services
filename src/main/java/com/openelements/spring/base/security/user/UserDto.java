package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.WithId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link UserEntity}.
 *
 * <p>Returned by {@link UserService#getCurrentUser()} and the user-related REST endpoints. The
 * {@link #avatarUrl()} points directly at the identity provider's avatar image — clients render it
 * in an {@code <img>} tag without any proxying or caching by this library. {@code avatarUrl} is
 * {@code null} when the identity provider does not assert an avatar for the user.
 */
@Schema(description = "User")
public record UserDto(
    @Schema(description = "User ID") UUID id,
    @Schema(description = "Display name") String name,
    @Schema(description = "Email address") String email,
    @Schema(description = "Avatar URL synchronized from the identity provider") String avatarUrl,
    @Schema(description = "Creation timestamp") Instant createdAt,
    @Schema(description = "Last update timestamp") Instant updatedAt)
    implements WithId {

  /**
   * Builds a DTO from the given entity.
   *
   * @param entity the source entity
   * @return the corresponding DTO
   */
  public static UserDto fromEntity(final UserEntity entity) {
    return new UserDto(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getAvatarUrl(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
