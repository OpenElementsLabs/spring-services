package com.openelements.spring.services.keys;

import com.openelements.spring.services.tenant.RepositoryWithTenantSupport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface KeyEntityRepository extends RepositoryWithTenantSupport<KeyEntity, UUID> {
    Optional<Object> findByPrincipal(String userName);
}
