package com.openelements.spring.services.tag;

import com.openelements.spring.services.data.WithId;

import java.time.Instant;
import java.util.UUID;

public record TagDto(UUID id, String name, String description, String color,
                     Instant createdAt, Instant updatedAt) implements WithId {

    public static TagDto fromEntity(final TagEntity entity) {
        return new TagDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getColor(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
