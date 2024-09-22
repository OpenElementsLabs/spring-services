package com.openelements.spring.services.tenant;

import com.openelements.spring.services.data.DataService;
import com.openelements.spring.services.data.WithId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractMultitenantService<E extends AbstractMultitenantEntity, D extends WithId> implements
        DataService<E, D> {

    protected abstract RepositoryWithTenantSupport<E, UUID> getRepository();

    protected abstract TenantService getTenantService();

    @Override
    public D save(D data) {
        Objects.requireNonNull(data, "data must not be null");
        final E savedEntity = getRepository().save(mapToEntity(data));
        return mapToData(savedEntity);
    }

    @Override
    public Optional<D> findById(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        return findById(UUID.fromString(id));
    }

    @Override
    public Optional<D> findById(final UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        final String tenantId = getCurrentTenant();
        return getRepository().findByIdAndTenantId(id, tenantId)
                .map(this::mapToData);
    }

    @Override
    public List<D> getAll() {
        return getRepository().findAllByTenantId(getCurrentTenant())
                .stream()
                .map(this::mapToData)
                .toList();
    }

    @Override
    public void delete(final D data) {
        Objects.requireNonNull(data, "data must not be null");
        delete(data.id());
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        delete(UUID.fromString(id));
    }

    @Override
    public void delete(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        final String tenantId = getCurrentTenant();
        final E entity = getRepository().findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found for tenant " + tenantId));
        getRepository().delete(entity);
    }

    protected abstract D mapToData(E entity);

    protected E mapToEntity(D data) {
        Objects.requireNonNull(data, "data must not be null");
        final UUID id = data.id();
        final E entity;
        final String tenantId = getCurrentTenant();
        if(id == null) {
            entity = createNewEntity();
            entity.setTenantId(tenantId);
        } else {
            entity = getRepository().findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found for tenant " + tenantId));
        }
        return updateEntity(entity, data);
    }

    protected abstract E updateEntity(E entity, D data);

    protected abstract E createNewEntity();

    protected String getCurrentTenant() {
        return getTenantService().getCurrentTenant();
    }

}
