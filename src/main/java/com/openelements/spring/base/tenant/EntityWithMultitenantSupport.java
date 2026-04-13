package com.openelements.spring.base.tenant;

import com.openelements.spring.base.data.DbEntity;

public interface EntityWithMultitenantSupport extends DbEntity {

    String getTenantId();

    void setTenantId(String tenantId);
}
