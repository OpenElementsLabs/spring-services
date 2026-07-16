package com.openelements.spring.base.tenant;

import com.openelements.spring.base.data.DbEntity;

/**
 * Marker contract for JPA entities that participate in row-level multi-tenancy.
 *
 * <p>Every implementation must persist a non-null {@code tenantId} value identifying the tenant
 * that owns the row. Most entities should extend {@link AbstractMultitenantEntity} rather than
 * implementing this interface directly — the abstract class adds the column mapping and a
 * pre-persist guard.
 */
public interface EntityWithMultitenantSupport extends DbEntity {

  /**
   * Returns the tenant id that owns this row.
   *
   * @return the tenant id, never {@code null} for persisted entities
   */
  String getTenantId();

  /**
   * Sets the tenant id. Application code should not call this directly — the tenant id is assigned
   * by {@link AbstractMultitenantDbBackedDataService} on insert.
   *
   * @param tenantId the tenant id to assign
   */
  void setTenantId(String tenantId);
}
