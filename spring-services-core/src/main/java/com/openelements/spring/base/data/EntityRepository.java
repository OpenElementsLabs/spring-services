package com.openelements.spring.base.data;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base Spring Data repository for any {@link DbEntity}. Locks the primary-key type to {@link UUID}
 * so all repositories in the platform share a consistent identifier strategy.
 *
 * <p>Annotated with {@link NoRepositoryBean} so Spring Data does not attempt to instantiate this
 * generic interface — only concrete subinterfaces are picked up.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * public interface BookRepository extends EntityRepository<BookEntity> {
 *   Optional<BookEntity> findByTitle(String title);
 * }
 * }</pre>
 *
 * @param <E> the entity type managed by this repository
 */
@NoRepositoryBean
public interface EntityRepository<E extends DbEntity> extends JpaRepository<E, UUID> {

  @NonNull
  default Stream<E> findAllByIds(@NonNull final Iterable<UUID> ids) {
    Objects.requireNonNull(ids, "ids must not be null");
    return findAllById(ids).stream();
  }

  @NonNull
  default E findByIdOrThrow(@NonNull final UUID id) {
    Objects.requireNonNull(id, "ids must not be null");
    return findById(id).orElseThrow(() -> new IllegalArgumentException("No such id " + id));
  }
}
