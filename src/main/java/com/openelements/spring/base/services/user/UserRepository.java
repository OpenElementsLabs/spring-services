package com.openelements.spring.base.services.user;

import com.openelements.spring.base.data.EntityRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link UserEntity}.
 */
public interface UserRepository extends EntityRepository<UserEntity> {

    /**
     * Looks up a user by the OAuth2 subject identifier ({@code sub} JWT claim).
     *
     * @param sub the OAuth2 subject identifier
     * @return the matching user, or an empty {@link Optional} if no local mirror has been provisioned
     * yet
     */
    Optional<UserEntity> findBySub(String sub);
}
