package com.openelements.spring.base.services.apikey;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.NameSupplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * JPA entity representing an API key in the CRM system. Keys are immutable after creation — only
 * create and delete.
 */
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity extends AbstractEntity {

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "key_hash", nullable = false, length = 64, unique = true)
  private String keyHash;

  @Column(name = "key_prefix", nullable = false, length = 20)
  private String keyPrefix;

  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  public ApiKeyEntity() {}

  @NameSupplier
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = Objects.requireNonNull(name, "name must not be null");
  }

  public String getKeyHash() {
    return keyHash;
  }

  public void setKeyHash(final String keyHash) {
    this.keyHash = Objects.requireNonNull(keyHash, "keyHash must not be null");
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(final String keyPrefix) {
    this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(final String createdBy) {
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
  }

  @Override
  public String toString() {
    return "ApiKeyEntity[id=" + id() + ", name=" + name + ", keyPrefix=" + keyPrefix + "]";
  }
}
