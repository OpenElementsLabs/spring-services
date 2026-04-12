package com.openelements.spring.services.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a key-value setting.
 */
@Entity
@Table(name = "settings")
public class SettingsEntity {

    @Id
    @Column(name = "`key`", length = 100)
    private String key;

    @Column(name = "`value`", nullable = false, columnDefinition = "TEXT")
    private String value;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    protected SettingsEntity() {
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
