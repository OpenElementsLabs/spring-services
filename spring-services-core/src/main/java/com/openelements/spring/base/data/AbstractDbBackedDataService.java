package com.openelements.spring.base.data;

import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template implementation of {@link DbBackedDataService} that takes care of the recurring
 * boilerplate every data service needs:
 *
 * <ul>
 *   <li>Translating between entities ({@code E}) and DTOs ({@code D}).
 *   <li>Distinguishing inserts from updates based on the DTO's id.
 *   <li>Wrapping all mutating operations in a Spring {@link Transactional} boundary.
 *   <li>Publishing {@link OnObjectCreate}, {@link OnObjectUpdate} and {@link OnObjectDelete} events
 *       so other components can react asynchronously to data changes.
 * </ul>
 *
 * <h2>Subclass responsibilities</h2>
 *
 * <p>Subclasses implement four small methods that describe <em>what</em> the entity looks like and
 * <em>how</em> it maps to the DTO; the rest of the lifecycle is handled by this class:
 *
 * <ol>
 *   <li>{@link #createDetachedEntity()} — return a new, unpersisted instance of {@code E}.
 *   <li>{@link #updateEntity(DbEntity, WithId)} — copy DTO fields onto the entity.
 *   <li>{@link #toData(DbEntity)} — build a DTO from a persisted entity.
 *   <li>{@link #getRepository()} — expose the {@link EntityRepository} this service delegates to.
 * </ol>
 *
 * <h2>Hook points</h2>
 *
 * <p>Subclasses may override the {@code preSave} / {@code postSave} / {@code preDelete} / {@code
 * postDelete} methods to implement additional validation, projection updates or custom event
 * publishing. When overriding {@code postSave} or {@code postDelete}, remember to delegate to
 * {@code super} (or to publish equivalent events) if the standard lifecycle events should still be
 * fired.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @Service
 * public class BookDataService extends AbstractDbBackedDataService<BookEntity, BookDto> {
 *
 *   private final BookRepository repository;
 *
 *   public BookDataService(BookRepository repository, ApplicationEventPublisher eventPublisher) {
 *     super(eventPublisher);
 *     this.repository = repository;
 *   }
 *
 *   @Override protected BookEntity createDetachedEntity() { return new BookEntity(); }
 *
 *   @Override protected void updateEntity(BookEntity entity, BookDto data) {
 *     entity.setTitle(data.title());
 *     entity.setAuthor(data.author());
 *   }
 *
 *   @Override protected BookDto toData(BookEntity entity) {
 *     return new BookDto(entity.getId(), entity.getTitle(), entity.getAuthor());
 *   }
 *
 *   @Override protected EntityRepository<BookEntity> getRepository() { return repository; }
 * }
 * }</pre>
 *
 * @param <E> the JPA entity type
 * @param <D> the DTO type exposed by this service
 */
@Transactional
public abstract class AbstractDbBackedDataService<E extends DbEntity, D extends WithId>
    implements DbBackedDataService<E, D> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractDbBackedDataService.class);

  private final ApplicationEventPublisher eventPublisher;

  private final boolean publishEvents;

  /**
   * @param eventPublisher Spring's event publisher, used to broadcast lifecycle events
   */
  public AbstractDbBackedDataService(@NonNull final ApplicationEventPublisher eventPublisher) {
    this(eventPublisher, true);
  }

  /**
   * Constructs a new service with explicit control over lifecycle event publishing. Use this
   * constructor when the service is itself involved in handling those events (for example an
   * audit-log service) and must therefore opt out of publishing them to avoid recursion.
   *
   * @param eventPublisher Spring's event publisher, used to broadcast lifecycle events
   * @param publishEvents whether {@code OnObjectCreate}, {@code OnObjectUpdate} and {@code
   *     OnObjectDelete} events should be published after each save or delete; pass {@code false} to
   *     suppress them
   */
  public AbstractDbBackedDataService(
      @NonNull final ApplicationEventPublisher eventPublisher, final boolean publishEvents) {
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    this.publishEvents = publishEvents;
  }

  /**
   * Returns a fresh, unpersisted entity instance — typically just {@code new MyEntity()}.
   *
   * @return a new entity instance
   */
  @NonNull
  protected abstract E createDetachedEntity();

  /**
   * Copies the relevant fields from {@code data} onto {@code entity}.
   *
   * <p>Implementations should not touch the entity's id or audit timestamps, since those are
   * managed by JPA and Hibernate respectively.
   *
   * @param entity the managed entity to mutate
   * @param data the DTO carrying the new values
   */
  protected abstract void updateEntity(@NonNull E entity, @NonNull D data);

  /**
   * Maps a persisted entity to its DTO representation.
   *
   * @param entity the persisted entity
   * @return the corresponding DTO
   */
  @NonNull
  protected abstract D toData(@NonNull E entity);

  /**
   * Returns the Spring Data repository this service delegates to.
   *
   * @return the underlying repository
   */
  @NonNull
  protected abstract EntityRepository<E> getRepository();

  /**
   * Hook invoked just before {@link #save(WithId)} writes the data to the database. Default
   * implementation does nothing.
   *
   * @param data the DTO that is about to be persisted
   */
  protected void preSave(@NonNull final D data) {}

  /**
   * Hook invoked just before {@link #delete(WithId)} removes the data from the database. Default
   * implementation does nothing.
   *
   * @param data the DTO that is about to be deleted
   */
  protected void preDelete(@NonNull final D data) {}

  /**
   * Hook invoked after a successful delete. Default implementation publishes an {@link
   * OnObjectDelete} event.
   *
   * @param data the DTO that was just deleted
   */
  protected void postDelete(@NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    if (!publishEvents) {
      return;
    }
    eventPublisher.publishEvent(new OnObjectDelete<>(data));
  }

  /**
   * Hook invoked after a successful save. Default implementation publishes an {@link
   * OnObjectCreate} event for inserts and an {@link OnObjectUpdate} event for updates.
   *
   * @param insert {@code true} if the operation was an insert, {@code false} for an update
   * @param data the DTO that was just persisted
   */
  protected void postSave(final boolean insert, @NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    if (!publishEvents) {
      return;
    }
    if (insert) {
      eventPublisher.publishEvent(new OnObjectCreate<>(data));
    } else {
      eventPublisher.publishEvent(new OnObjectUpdate<>(data));
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Performs an upsert: a new entity is created when the DTO has no id; otherwise the existing
   * entity is loaded, updated and saved. The matching {@code preSave} / {@code postSave} hooks are
   * invoked before and after the database round-trip.
   *
   * @throws IllegalArgumentException if the DTO carries an id but no entity with that id exists
   */
  @NonNull
  @Override
  public D save(@NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    preSave(data);
    if (data.id() == null) {
      LOG.debug("Insert new entity: {}", data);
      final E entity = createDetachedEntity();
      updateEntity(entity, data);
      final D saved = toData(getRepository().save(entity));
      LOG.debug("New entity inserted: {}", saved);
      postSave(true, saved);
      return saved;
    } else {
      LOG.debug("Update entity: {}", data);
      final E entity =
          getRepository()
              .findById(data.id())
              .orElseThrow(
                  () -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
      updateEntity(entity, data);
      final D saved = toData(getRepository().save(entity));
      LOG.debug("entity updated: {}", data);
      postSave(false, saved);
      return saved;
    }
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public List<D> getAll() {
    return getRepository().findAll().stream().map(this::toData).toList();
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  public Optional<D> findById(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    return getRepository().findById(id).map(this::toData);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Loads the entity matching {@code data.id()}, deletes it, and triggers the {@code preDelete}
   * / {@code postDelete} hooks.
   *
   * @throws IllegalArgumentException if no entity with the DTO's id exists
   */
  @Override
  public void delete(@NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    preDelete(data);
    final E entity =
        getRepository()
            .findById(data.id())
            .orElseThrow(
                () -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
    LOG.debug("Deleting entity: {}", entity);
    getRepository().delete(entity);
    LOG.debug("Entity deleted");
    postDelete(data);
  }

  /** {@inheritDoc} */
  @NonNull
  public Page<D> findAll(@NonNull final Pageable pageable) {
    Objects.requireNonNull(pageable, "pageable must not be null");
    final Page<E> page = getRepository().findAll(pageable);
    return page.map(e -> toData(e));
  }

  /**
   * Returns whether an entity with the given id exists.
   *
   * @param id the id to check
   * @return {@code true} if an entity with that id exists, {@code false} otherwise
   */
  public boolean existsById(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    return getRepository().existsById(id);
  }
}
