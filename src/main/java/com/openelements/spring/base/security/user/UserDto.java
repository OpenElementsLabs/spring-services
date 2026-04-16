package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.WithId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link UserEntity}.
 *
 * <p>Returned by {@link UserService#getCurrentUser()} and the user-related REST endpoints. The
 * avatar bytes are intentionally <em>not</em> embedded — clients must fetch the image separately
 * via the avatar endpoint. The {@link #hasAvatar()} flag lets the UI decide whether to issue that
 * extra request.
 */
@Schema(description = "User")
public record UserDto(
    @Schema(description = "User ID") UUID id,
    @Schema(description = "Display name") String name,
    @Schema(description = "Email address") String email,
    @Schema(description = "Whether the user has an avatar") boolean hasAvatar,
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
        entity.getAvatar() != null,
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
