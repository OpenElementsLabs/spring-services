package com.openelements.spring.services.tenant;

import com.openelements.spring.services.data.DbBackedDataService;
import com.openelements.spring.services.data.WithId;
import com.openelements.spring.services.events.OnObjectCreate;
import com.openelements.spring.services.events.OnObjectDelete;
import com.openelements.spring.services.events.OnObjectUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Transactional
public abstract class AbstractMultitenantService<E extends AbstractMultitenantEntity, D extends WithId> implements
        DbBackedDataService<E, D> {

    private final ApplicationEventPublisher eventPublisher;

    protected AbstractMultitenantService(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    protected abstract RepositoryWithTenantSupport<E, UUID> getRepository();

    protected abstract TenantService getTenantService();

    @Override
    public D save(D data) {
        Objects.requireNonNull(data, "data must not be null");
        final UUID id = data.id();
        final String tenantId = getCurrentTenant();
        preSave(data);
        if (id == null) {
            final E entity = createNewEntity();
            entity.setTenantId(tenantId);
            updateEntity(entity, data);
            final E savedEntity = getRepository().save(entity);
            final D savedData = mapToData(savedEntity);
            postSave(true, savedData);
            return savedData;
        } else {
            final E entity = getRepository().findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found for tenant " + tenantId));
            updateEntity(entity, data);
            getRepository().save(entity);
            final E savedEntity = getRepository().save(entity);
            final D savedData = mapToData(savedEntity);
            postSave(false, savedData);
            return savedData;
        }
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
        final UUID id = data.id();
        if (id == null) {
            throw new IllegalArgumentException("Cannot delete entity without id");
        }
        final String tenantId = getCurrentTenant();
        preDelete(data);
        final E entity = getRepository().findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found for tenant " + tenantId));
        getRepository().delete(entity);
        postDelete(data);
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

    protected abstract E updateEntity(E entity, D data);

    protected abstract E createNewEntity();

    protected String getCurrentTenant() {
        return getTenantService().getCurrentTenant();
    }

    protected void preSave(final D data) {
    }

    protected void preDelete(final D data) {
    }

    protected void postDelete(D data) {
        eventPublisher.publishEvent(new OnObjectDelete<>(data));
    }

    protected void postSave(final boolean insert, D data) {
        if (insert) {
            eventPublisher.publishEvent(new OnObjectCreate<>(data));
        } else {
            eventPublisher.publishEvent(new OnObjectUpdate<>(data));
        }
    }

}
