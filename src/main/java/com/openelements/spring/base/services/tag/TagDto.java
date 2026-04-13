package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.WithId;

import java.util.UUID;

public record TagDto(UUID id, String name, String description, String color) implements WithId {

    public static TagDto fromEntity(final TagEntity entity) {
        return new TagDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getColor()
        );
    }
}
