package com.openelements.spring.base.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataService<D extends WithId> {

  D save(final D data);

  List<D> getAll();

  default Optional<D> findById(final String id) {
    return findById(UUID.fromString(id));
  }

  Optional<D> findById(final UUID id);

  default void delete(final String id) {
    delete(UUID.fromString(id));
  }

  default void delete(final UUID id) {
    final D data =
        findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found"));
    delete(data);
  }

  void delete(final D data);
}
