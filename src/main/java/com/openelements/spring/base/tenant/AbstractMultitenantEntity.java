package com.openelements.spring.base.tenant;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@MappedSuperclass
public class AbstractMultitenantEntity extends AbstractEntity implements EntityWithMultitenantSupport {

    @Column(nullable = false)
    private String tenantId;

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    @PreUpdate
    public final void checkTenant() {
        if (tenantId == null) {
            throw new IllegalStateException("Tenant ID must be set before persisting");
        }
    }
}
