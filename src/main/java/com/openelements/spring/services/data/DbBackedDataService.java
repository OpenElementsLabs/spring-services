package com.openelements.spring.services.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DbBackedDataService<E extends DbEntity, D extends WithId> extends DataService<D> {

    Page<D> findAll(final Pageable pageable);
}
