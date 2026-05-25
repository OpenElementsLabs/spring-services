package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.NameSupplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * JPA entity for a tag — a small, named, color-coded label that other features can attach to their
 * domain objects.
 *
 * <p>The {@code name} column is unique (case-sensitive at the storage level; case-insensitive
 * lookup is provided by {@link TagRepository#findByNameIgnoreCase(String)}). The {@code color} is
 * stored as a 7-character string, intended to hold a CSS hex code such as {@code #aabbcc}.
 */
@Entity
@Table(name = "tags")
public class TagEntity extends AbstractEntity {

  @Column(nullable = false, unique = true)
  private String name;

  @Column private String description;

  @Column(nullable = false, length = 7)
  private String color;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @NameSupplier
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getColor() {
    return color;
  }

  public void setColor(final String color) {
    this.color = color;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
