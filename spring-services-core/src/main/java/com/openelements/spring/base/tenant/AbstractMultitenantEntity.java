package com.openelements.spring.base.tenant;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Mapped superclass for all tenant-scoped JPA entities.
 *
 * <p>Extends {@link AbstractEntity} (UUID primary key + audit timestamps) and adds a non-null
 * {@code tenantId} column. The {@link #checkTenant() pre-persist / pre-update callback} fails fast
 * if the tenant id has not been assigned, which prevents the most common multi-tenancy bug —
 * accidentally writing a row that no tenant owns.
 *
 * <p>Application code never sets the tenant id manually: {@link
 * AbstractMultitenantDbBackedDataService} populates it from {@link
 * TenantService#getCurrentTenant()} on insert.
 */
@MappedSuperclass
public class AbstractMultitenantEntity extends AbstractEntity
    implements EntityWithMultitenantSupport {

  /** The id of the tenant that owns this row; assigned by the owning data service on insert. */
  @Column(nullable = false)
  private String tenantId;

  /** Creates a new tenant-scoped entity; the tenant id is assigned by the owning data service. */
  public AbstractMultitenantEntity() {}

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  /**
   * JPA lifecycle callback that aborts an insert or update if the tenant id is missing. Declared
   * {@code final} so subclasses cannot accidentally bypass the check.
   *
   * @throws IllegalStateException if {@code tenantId} is {@code null}
   */
  @PrePersist
  @PreUpdate
  public final void checkTenant() {
    if (tenantId == null) {
      throw new IllegalStateException("Tenant ID must be set before persisting");
    }
  }
}
