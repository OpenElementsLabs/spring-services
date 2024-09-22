package com.openelements.spring.services.tenant;

import com.openelements.spring.services.data.DbEntity;

public interface EntityWithMultitenantSupport extends DbEntity {

    String getTenantId();

    void setTenantId(String tenantId);
}
