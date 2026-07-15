package com.openelements.spring.base.tenant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Spring Data repository contract for {@link EntityWithMultitenantSupport} entities.
 *
 * <p>Deliberately does <em>not</em> extend the standard {@link
 * org.springframework.data.jpa.repository.JpaRepository} or {@link
 * com.openelements.spring.base.data.EntityRepository} — those expose unfiltered finders such as
 * {@code findById} and {@code findAll}, which would silently return rows belonging to other
 * tenants. Concrete subinterfaces should only add additional tenant-filtered queries.
 *
 * <p>Annotated with {@link NoRepositoryBean} so Spring Data does not attempt to instantiate this
 * generic interface — only concrete subinterfaces are picked up.
 *
 * @param <T> the entity type managed by this repository
 * @param <I> the entity's id type (typically {@link java.util.UUID})
 */
@NoRepositoryBean
public interface RepositoryWithTenantSupport<T extends EntityWithMultitenantSupport, I>
    extends Repository<T, I> {

  /**
   * Returns all rows owned by the given tenant.
   *
   * @param tenantId the tenant id to filter by
   * @return the matching rows, never {@code null}
   */
  List<T> findAllByTenantId(String tenantId);

  /**
   * Looks up a row by id, but only returns it if it belongs to the given tenant.
   *
   * @param id the row id
   * @param tenantId the tenant id the row must belong to
   * @return the matching row, or empty if no row with that id is owned by that tenant
   */
  Optional<T> findByIdAndTenantId(I id, String tenantId);

  /**
   * Inserts or updates the given entity. The tenant id must already be set on the entity (see
   * {@link AbstractMultitenantEntity#checkTenant()}).
   *
   * @param entity the entity to persist
   * @return the persisted entity
   */
  T save(T entity);

  /**
   * Deletes the given entity. Callers must ensure the entity belongs to the current tenant —
   * typically by loading it via {@link #findByIdAndTenantId(Object, String)} first.
   *
   * @param entity the entity to delete
   */
  void delete(T entity);
}
