package com.openelements.spring.services.keys;

import com.openelements.spring.services.tenant.AbstractMultitenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class KeyEntity extends AbstractMultitenantEntity {

    @Column(nullable = false)
    private String principal;

    @Column(nullable = false)
    private String key;

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getKey() {
        return key;
    }
}
