package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.EntityRepository;
import java.util.*;
import java.util.stream.Collectors;

public interface TagRepository extends EntityRepository<TagEntity> {

  Optional<TagEntity> findByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCase(String name);

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
