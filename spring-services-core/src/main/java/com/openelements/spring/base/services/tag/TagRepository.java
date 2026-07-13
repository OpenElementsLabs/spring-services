package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.EntityRepository;
import java.util.*;
import java.util.stream.Collectors;

/** Spring Data repository for {@link TagEntity}. */
public interface TagRepository extends EntityRepository<TagEntity> {

  /**
   * Looks up a tag by name, ignoring case.
   *
   * @param name the tag name
   * @return the matching tag, or empty if none exists
   */
  Optional<TagEntity> findByNameIgnoreCase(String name);

  /**
   * Returns whether a tag with the given name exists, ignoring case.
   *
   * @param name the tag name
   * @return {@code true} if a matching tag exists, {@code false} otherwise
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Loads all tags for the given list of ids and returns them as a set.
   *
   * <p>This is an "all-or-nothing" lookup — if any id does not match an existing tag, an {@link
   * IllegalArgumentException} is thrown. Useful when validating user-supplied tag references before
   * associating them with a domain object.
   *
   * @param tagIds the tag ids to load
   * @return the matching tags as an unmodifiable set
   * @throws IllegalArgumentException if any id does not match an existing tag
   */
  default Set<TagEntity> findAll(List<UUID> tagIds) {
    Objects.requireNonNull(tagIds, "tagIds must not be null");
    return tagIds.stream()
        .map(
            tagId ->
                findById(tagId)
                    .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId)))
        .collect(Collectors.toUnmodifiableSet());
  }
}
