package com.openelements.spring.base.data;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DbBackedDataService<E extends DbEntity, D extends WithId> extends DataService<D> {

    @NonNull Page<D> findAll(@NonNull final Pageable pageable);
}
