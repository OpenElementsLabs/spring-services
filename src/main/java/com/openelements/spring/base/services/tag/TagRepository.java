package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.EntityRepository;

import java.util.Optional;

public interface TagRepository extends EntityRepository<TagEntity> {

    Optional<TagEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
