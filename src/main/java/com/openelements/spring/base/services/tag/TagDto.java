package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.WithId;
import java.util.UUID;

/**
 * Public projection of a {@link TagEntity} returned by {@link TagDataService}.
 *
 * @param id the tag id, or {@code null} when used to request a new tag
 * @param name the unique tag name
 * @param description an optional human-readable description
 * @param color the display color, expected to be a 7-character CSS hex code (e.g. {@code #aabbcc})
 */
public record TagDto(UUID id, String name, String description, String color) implements WithId {

  /**
   * Builds a DTO from the given entity.
   *
   * @param entity the source entity
   * @return the corresponding DTO
   */
  public static TagDto fromEntity(final TagEntity entity) {
    return new TagDto(entity.getId(), entity.getName(), entity.getDescription(), entity.getColor());
  }
}
