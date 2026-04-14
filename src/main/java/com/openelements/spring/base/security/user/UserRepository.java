package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.EntityRepository;
import java.util.Optional;

public interface UserRepository extends EntityRepository<UserEntity> {
  Optional<UserEntity> findBySub(String sub);
}
