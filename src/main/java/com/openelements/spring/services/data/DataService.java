package com.openelements.spring.services.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataService<E extends DbEntity, D extends WithId> {

    D save(final D data);

    List<D> getAll();

    Optional<D> findById(final String id);

    Optional<D> findById(final UUID id);

    void delete(final String id);

    void delete(final UUID id);

    void delete(final D data);
}