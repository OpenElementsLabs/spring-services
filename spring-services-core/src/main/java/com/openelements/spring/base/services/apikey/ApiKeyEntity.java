package com.openelements.spring.base.services.apikey;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.DbSchema;
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
@Table(name = "api_keys", schema = DbSchema.NAME)
public class ApiKeyEntity extends AbstractEntity {

  /** The user-given name of the API key. */
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /** The SHA-256 hash of the raw key; the raw key itself is never stored. */
  @Column(name = "key_hash", nullable = false, length = 64, unique = true)
  private String keyHash;

  /** The non-secret display prefix of the key. */
  @Column(name = "key_prefix", nullable = false, length = 20)
  private String keyPrefix;

  /** The name of the user who created the key. */
  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  /** Creates a new, empty API key entity for use by JPA and callers building a key to persist. */
  public ApiKeyEntity() {}

  /**
   * Returns the user-given name of this API key.
   *
   * @return the name of the key
   */
  @NameSupplier
  public String getName() {
    return name;
  }

  /**
   * Sets the user-given name of this API key.
   *
   * @param name the name of the key
   */
  public void setName(final String name) {
    this.name = Objects.requireNonNull(name, "name must not be null");
  }

  /**
   * Returns the SHA-256 hash of the raw key, which is the only representation stored.
   *
   * @return the hex-encoded hash of the key
   */
  public String getKeyHash() {
    return keyHash;
  }

  /**
   * Sets the SHA-256 hash of the raw key.
   *
   * @param keyHash the hex-encoded hash of the key
   */
  public void setKeyHash(final String keyHash) {
    this.keyHash = Objects.requireNonNull(keyHash, "keyHash must not be null");
  }

  /**
   * Returns the non-secret display prefix of the key.
   *
   * @return the display prefix of the key
   */
  public String getKeyPrefix() {
    return keyPrefix;
  }

  /**
   * Sets the non-secret display prefix of the key.
   *
   * @param keyPrefix the display prefix of the key
   */
  public void setKeyPrefix(final String keyPrefix) {
    this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
  }

  /**
   * Returns the name of the user who created this key.
   *
   * @return the name of the creating user
   */
  public String getCreatedBy() {
    return createdBy;
  }

  /**
   * Sets the name of the user who created this key.
   *
   * @param createdBy the name of the creating user
   */
  public void setCreatedBy(final String createdBy) {
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
  }

  @Override
  public String toString() {
    return "ApiKeyEntity[id=" + id() + ", name=" + name + ", keyPrefix=" + keyPrefix + "]";
  }
}
