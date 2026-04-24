package com.openelements.spring.base.data;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * Generic CRUD contract for a service that manages DTOs of type {@code D}.
 *
 * <p>The interface deliberately speaks only in terms of DTOs — the underlying persistence model is
 * an implementation detail. Use {@link DbBackedDataService} when you need additional database-only
 * features such as pagination.
 *
 * <h2>Behavioural contract</h2>
 *
 * <ul>
 *   <li>{@link #save(WithId)} performs an upsert: if {@link WithId#id()} is {@code null} the
 *       implementation must create a new record, otherwise it must update the existing one.
 *   <li>{@link #findById(UUID)} returns an empty {@link Optional} when the id is unknown — it must
 *       <em>not</em> throw.
 *   <li>{@link #delete(UUID)} and {@link #delete(String)} throw {@link IllegalArgumentException}
 *       when no entity with the given id exists. {@link #delete(WithId)} requires the data object
 *       to expose a non-null id.
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * BookDataService books = ...;
 *
 * // create
 * BookDto created = books.save(new BookDto(null, "Effective Java", "Joshua Bloch"));
 *
 * // read
 * BookDto reloaded = books.findById(created.id()).orElseThrow();
 * List<BookDto> all = books.getAll();
 *
 * // update
 * books.save(new BookDto(created.id(), "Effective Java, 3rd ed.", "Joshua Bloch"));
 *
 * // delete
 * books.delete(created.id());
 * }</pre>
 *
 * @param <D> the DTO type managed by this service
 */
public interface DataService<D extends WithId> {

  /**
   * Inserts or updates the given DTO.
   *
   * @param data the DTO to persist; an insert is performed if {@link WithId#id()} is {@code null},
   *     otherwise an update of the existing record is performed
   * @return the persisted DTO, including the generated id and any server-side defaults
   */
  @NonNull D save(@NonNull final D data);

  /**
   * Returns all stored DTOs.
   *
   * <p>This method materialises every record. Prefer {@link DbBackedDataService#findAll(
   * org.springframework.data.domain.Pageable)} for collections that may grow beyond a few hundred
   * entries.
   *
   * @return all stored DTOs, never {@code null}
   */
  @NonNull List<D> getAll();

  /**
   * Convenience overload of {@link #findById(UUID)} that parses the id from its string
   * representation.
   *
   * @param id the id as a string
   * @return the matching DTO, or an empty {@link Optional} if none exists
   * @throws IllegalArgumentException if {@code id} is not a valid UUID
   */
  default @NonNull Optional<D> findById(@NonNull final String id) {
    Objects.requireNonNull(id, "id must not be null");
    return findById(UUID.fromString(id));
  }

  /**
   * Looks up a DTO by its id.
   *
   * @param id the id to look up
   * @return the matching DTO, or an empty {@link Optional} if none exists
   */
  @NonNull Optional<D> findById(@NonNull final UUID id);

  default @NonNull D findByIdOrThrow(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    return findById(id).orElseThrow(() -> new IllegalArgumentException("No such id " + id));
  }

  /**
   * Convenience overload of {@link #delete(UUID)} that parses the id from its string
   * representation.
   *
   * @param id the id as a string
   * @throws IllegalArgumentException if {@code id} is not a valid UUID, or if no entity with that
   *     id exists
   */
  default void delete(@NonNull final String id) {
    Objects.requireNonNull(id, "id must not be null");
    delete(UUID.fromString(id));
  }

  /**
   * Deletes the entity identified by the given id.
   *
   * @param id the id of the entity to delete
   * @throws IllegalArgumentException if no entity with that id exists
   */
  default void delete(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    final D data =
        findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Entity with id " + id + " not found"));
    delete(data);
  }

  /**
   * Deletes the entity matching the given DTO.
   *
   * @param data the DTO whose id identifies the entity to delete; must have a non-null id
   * @throws IllegalArgumentException if no entity with that id exists
   */
  void delete(@NonNull final D data);
}
