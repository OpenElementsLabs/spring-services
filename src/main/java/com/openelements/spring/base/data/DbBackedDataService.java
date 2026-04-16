package com.openelements.spring.base.data;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Extension of {@link DataService} for services that are backed by a relational database and can
 * therefore expose efficient pagination.
 *
 * <p>The DTO type {@code D} and the entity type {@code E} are kept distinct so that the public API
 * surface (DTOs) and the persistence model (entities) can evolve independently.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * DbBackedDataService<BookEntity, BookDto> books = ...;
 * Page<BookDto> page = books.findAll(PageRequest.of(0, 20, Sort.by("title")));
 * }</pre>
 *
 * @param <E> the JPA entity type
 * @param <D> the DTO type exposed by this service
 */
public interface DbBackedDataService<E extends DbEntity, D extends WithId> extends DataService<D> {

  /**
   * Returns a page of DTOs according to the given paging and sorting parameters.
   *
   * @param pageable paging and sorting information
   * @return a page of DTOs, never {@code null}
   */
  @NonNull Page<D> findAll(@NonNull final Pageable pageable);
}
