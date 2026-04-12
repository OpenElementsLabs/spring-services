package com.openelements.spring.services.tag;

import com.openelements.spring.services.data.EntityRepository;

import java.util.Optional;

public interface TagRepository extends EntityRepository<TagEntity> {

    Optional<TagEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
