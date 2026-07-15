package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.DbSchema;
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
@Table(name = "tags", schema = DbSchema.NAME)
public class TagEntity extends AbstractEntity {

  /** The unique name of the tag. */
  @Column(nullable = false, unique = true)
  private String name;

  /** The optional description of the tag. */
  @Column private String description;

  /** The color of the tag as a CSS hex code (e.g. {@code #aabbcc}). */
  @Column(nullable = false, length = 7)
  private String color;

  /** The last-update timestamp of the tag. */
  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Creates a new, empty tag entity for use by JPA and the owning service. */
  public TagEntity() {}

  /**
   * Returns the unique name of this tag.
   *
   * @return the tag name
   */
  @NameSupplier
  public String getName() {
    return name;
  }

  /**
   * Sets the unique name of this tag.
   *
   * @param name the tag name
   */
  public void setName(final String name) {
    this.name = name;
  }

  /**
   * Returns the optional description of this tag.
   *
   * @return the tag description, or {@code null} if none is set
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the optional description of this tag.
   *
   * @param description the tag description, or {@code null} to clear it
   */
  public void setDescription(final String description) {
    this.description = description;
  }

  /**
   * Returns the color of this tag as a CSS hex code.
   *
   * @return the tag color (e.g. {@code #aabbcc})
   */
  public String getColor() {
    return color;
  }

  /**
   * Sets the color of this tag as a CSS hex code.
   *
   * @param color the tag color (e.g. {@code #aabbcc})
   */
  public void setColor(final String color) {
    this.color = color;
  }

  /**
   * Returns the timestamp at which this tag was last updated.
   *
   * @return the last update timestamp
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
