package com.openelements.spring.base.data;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

public interface DataService<D extends WithId> {

  @NonNull D save(@NonNull final D data);

  @NonNull List<D> getAll();

  default @NonNull Optional<D> findById(@NonNull final String id) {
    Objects.requireNonNull(id, "id must not be null");
    return findById(UUID.fromString(id));
  }

  @NonNull Optional<D> findById(@NonNull final UUID id);

  default void delete(@NonNull final String id) {
    Objects.requireNonNull(id, "id must not be null");
    delete(UUID.fromString(id));
  }

  default void delete(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    final D data =
        findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found"));
    delete(data);
  }

  void delete(@NonNull final D data);
}
