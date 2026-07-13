package com.openelements.spring.base.tenant;

import com.openelements.spring.base.data.DbBackedDataService;
import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-aware counterpart of {@link
 * com.openelements.spring.base.data.AbstractDbBackedDataService}.
 *
 * <p>Behaves identically to its non-tenant sibling with two important additions:
 *
 * <ol>
 *   <li>Every insert sets the entity's {@code tenantId} to the value returned by {@link
 *       TenantService#getCurrentTenant()}.
 *   <li>Every read, update and delete uses tenant-filtered repository methods ({@link
 *       RepositoryWithTenantSupport#findAllByTenantId(String)} / {@link
 *       RepositoryWithTenantSupport#findByIdAndTenantId(Object, String)}). A row that belongs to a
 *       different tenant is indistinguishable from a non-existent row to callers of this service.
 * </ol>
 *
 * <p>As with the non-tenant variant, {@link OnObjectCreate}, {@link OnObjectUpdate} and {@link
 * OnObjectDelete} events are published from the {@code preSave} / {@code postSave} / {@code
 * preDelete} / {@code postDelete} hooks.
 *
 * @param <E> the multitenant JPA entity type
 * @param <D> the DTO type exposed by this service
 */
@Transactional
public abstract class AbstractMultitenantDbBackedDataService<
        E extends AbstractMultitenantEntity, D extends WithId>
    implements DbBackedDataService<E, D> {

  private final ApplicationEventPublisher eventPublisher;

  /**
   * @param eventPublisher Spring's event publisher, used to broadcast lifecycle events
   */
  protected AbstractMultitenantDbBackedDataService(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
  }

  /**
   * Returns the tenant-filtering repository this service delegates to.
   *
   * @return the underlying repository
   */
  protected abstract RepositoryWithTenantSupport<E, UUID> getRepository();

  /**
   * Returns the {@link TenantService} used to resolve the current tenant id. Exposed as a method
   * (rather than injected on the base class) so that subclasses retain full control over their
   * dependencies.
   *
   * @return the tenant service
   */
  protected abstract TenantService getTenantService();

  /**
   * {@inheritDoc}
   *
   * <p>On insert: creates a new entity, assigns the current tenant id, copies the DTO fields and
   * persists. On update: loads the existing row scoped to the current tenant — throwing if the row
   * either does not exist or belongs to another tenant — applies the DTO changes and saves.
   */
  @Override
  public D save(D data) {
    Objects.requireNonNull(data, "data must not be null");
    final UUID id = data.id();
    final String tenantId = getCurrentTenant();
    preSave(data);
    if (id == null) {
      final E entity = createNewEntity();
      entity.setTenantId(tenantId);
      updateEntity(entity, data);
      final E savedEntity = getRepository().save(entity);
      final D savedData = mapToData(savedEntity);
      postSave(true, savedData);
      return savedData;
    } else {
      final E entity =
          getRepository()
              .findByIdAndTenantId(id, tenantId)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Entity with id " + id + " not found for tenant " + tenantId));
      updateEntity(entity, data);
      getRepository().save(entity);
      final E savedEntity = getRepository().save(entity);
      final D savedData = mapToData(savedEntity);
      postSave(false, savedData);
      return savedData;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<D> findById(final String id) {
    Objects.requireNonNull(id, "id must not be null");
    return findById(UUID.fromString(id));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns empty if no row with the given id exists <em>or</em> if the row belongs to another
   * tenant.
   */
  @Override
  public Optional<D> findById(final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    final String tenantId = getCurrentTenant();
    return getRepository().findByIdAndTenantId(id, tenantId).map(this::mapToData);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Only returns rows owned by the current tenant.
   */
  @Override
  public List<D> getAll() {
    return getRepository().findAllByTenantId(getCurrentTenant()).stream()
        .map(this::mapToData)
        .toList();
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if the DTO has no id, or if no row with that id is owned by
   *     the current tenant
   */
  @Override
  public void delete(final D data) {
    Objects.requireNonNull(data, "data must not be null");
    final UUID id = data.id();
    if (id == null) {
      throw new IllegalArgumentException("Cannot delete entity without id");
    }
    final String tenantId = getCurrentTenant();
    preDelete(data);
    final E entity =
        getRepository()
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Entity with id " + id + " not found for tenant " + tenantId));
    getRepository().delete(entity);
    postDelete(data);
  }

  /** {@inheritDoc} */
  @Override
  public void delete(String id) {
    Objects.requireNonNull(id, "id must not be null");
    delete(UUID.fromString(id));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Deletes the row only if it is owned by the current tenant.
   *
   * @throws IllegalArgumentException if no row with the given id is owned by the current tenant
   */
  @Override
  public void delete(UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    final String tenantId = getCurrentTenant();
    final E entity =
        getRepository()
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Entity with id " + id + " not found for tenant " + tenantId));
    getRepository().delete(entity);
  }

  /**
   * Maps a persisted entity to its DTO representation.
   *
   * @param entity the persisted entity
   * @return the corresponding DTO
   */
  protected abstract D mapToData(E entity);

  /**
   * Copies the relevant fields from {@code data} onto {@code entity}. Implementations should not
   * touch the entity's id, tenant id or audit timestamps — those are managed by the framework.
   *
   * @param entity the managed entity to mutate
   * @param data the DTO carrying the new values
   * @return the mutated entity (typically the same instance as {@code entity})
   */
  protected abstract E updateEntity(E entity, D data);

  /**
   * Returns a fresh, unpersisted entity instance — typically just {@code new MyEntity()}.
   *
   * @return a new entity instance
   */
  protected abstract E createNewEntity();

  /**
   * Returns the tenant id that owns the current request. Defaults to delegating to {@link
   * #getTenantService()} but can be overridden in tests.
   *
   * @return the current tenant id
   */
  protected String getCurrentTenant() {
    return getTenantService().getCurrentTenant();
  }

  /**
   * Hook invoked just before {@link #save(WithId)} writes the data to the database. Default
   * implementation does nothing.
   *
   * @param data the DTO that is about to be persisted
   */
  protected void preSave(final D data) {}

  /**
   * Hook invoked just before {@link #delete(WithId)} removes the data from the database. Default
   * implementation does nothing.
   *
   * @param data the DTO that is about to be deleted
   */
  protected void preDelete(final D data) {}

  /**
   * Hook invoked after a successful delete. Default implementation publishes an {@link
   * OnObjectDelete} event.
   *
   * @param data the DTO that was just deleted
   */
  protected void postDelete(D data) {
    eventPublisher.publishEvent(new OnObjectDelete<>(data));
  }

  /**
   * Hook invoked after a successful save. Default implementation publishes an {@link
   * OnObjectCreate} event for inserts and an {@link OnObjectUpdate} event for updates.
   *
   * @param insert {@code true} if the operation was an insert, {@code false} for an update
   * @param data the DTO that was just persisted
   */
  protected void postSave(final boolean insert, D data) {
    if (insert) {
      eventPublisher.publishEvent(new OnObjectCreate<>(data));
    } else {
      eventPublisher.publishEvent(new OnObjectUpdate<>(data));
    }
  }
}
