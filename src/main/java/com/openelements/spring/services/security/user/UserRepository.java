package com.openelements.spring.services.security.user;

import com.openelements.spring.services.data.EntityRepository;

import java.util.Optional;

public interface UserRepository extends EntityRepository<UserEntity> {
    Optional<UserEntity> findBySub(String sub);
}
