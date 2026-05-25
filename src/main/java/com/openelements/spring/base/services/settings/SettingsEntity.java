package com.openelements.spring.base.services.settings;

import com.openelements.spring.base.data.NameSupplier;
import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * JPA entity representing a key-value setting.
 */
@Entity
@Table(name = "settings")
public class SettingsEntity extends AbstractEntity {

    @Column(name = "`key`", length = 100, nullable = false, unique = true)
    private String key;

    @Column(name = "`value`", nullable = false, columnDefinition = "TEXT")
    private String value;

    protected SettingsEntity() {
    }

    @NameSupplier
    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = Objects.requireNonNull(key, "key must not be null");
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }
}
