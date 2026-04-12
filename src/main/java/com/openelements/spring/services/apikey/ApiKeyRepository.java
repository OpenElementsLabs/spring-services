package com.openelements.spring.services.apikey;

import com.openelements.spring.services.data.EntityRepository;

import java.util.Optional;

/**
 * Repository for API key entities.
 */
public interface ApiKeyRepository extends EntityRepository<ApiKeyEntity> {

    /**
     * Finds an API key by its SHA-256 hash.
     *
     * @param keyHash the SHA-256 hex digest of the raw key
     * @return the API key entity if found
     */
    Optional<ApiKeyEntity> findByKeyHash(String keyHash);
}
