package com.openelements.spring.services.security.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "User")
public record UserDto(
        @Schema(description = "User ID") UUID id,
        @Schema(description = "Display name") String name,
        @Schema(description = "Email address") String email,
        @Schema(description = "Whether the user has an avatar") boolean hasAvatar,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt
) {

    public static UserDto fromEntity(final UserEntity entity) {
        return new UserDto(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getAvatar() != null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
