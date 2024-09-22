package com.openelements.spring.services.tenant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface RepositoryWithTenantSupport<T extends EntityWithMultitenantSupport, I> extends Repository<T, I> {

    List<T> findAllByTenantId(String tenantId);

    Optional<T> findByIdAndTenantId(I id, String tenantId);

    T save(T entity);

    void delete(T entity);
}
