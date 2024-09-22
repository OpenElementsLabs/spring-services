package com.openelements.spring.services.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractDataService<E extends DbEntity, D extends WithId> implements DataService<E, D> {

    protected abstract E createDetachedEntity();

    protected abstract void updateEntity(E entity, D data);

    protected abstract D toData(E entity);

    protected abstract EntityRepository<E> getRepository();

    @Override
    public D save(final D data) {
        if(data.id() != null) {
            final E entity = createDetachedEntity();
            updateEntity(entity, data);
            return toData(getRepository().save(entity));
        } else {
            final E entity = getRepository().findById(data.id())
                    .orElseThrow(() -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
            updateEntity(entity, data);
            return toData(getRepository().save(entity));
        }
    }

    @Override
    public List<D> getAll() {
        return getRepository().findAll().stream()
                .map(this::toData)
                .toList();
    }

    @Override
    public Optional<D> findById(final String id) {
        return findById(UUID.fromString(id));
    }

    @Override
    public Optional<D> findById(final UUID id) {
        return getRepository().findById(id)
                .map(this::toData);
    }

    @Override
    public void delete(final D data) {
        delete(data.id());
    }

    @Override
    public void delete(final String id) {
        delete(UUID.fromString(id));
    }

    @Override
    public void delete(final UUID id) {
        final E entity = getRepository().findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found"));
        getRepository().delete(entity);
    }
}
