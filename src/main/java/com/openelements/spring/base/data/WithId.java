package com.openelements.spring.base.data;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Marker contract for any object — typically a DTO or an entity — that is identified by a {@link
 * UUID}.
 *
 * <p>The id is intentionally nullable: a freshly constructed DTO that has not yet been persisted
 * will report {@code null}, which the {@link DataService} contract uses to distinguish an
 * <em>insert</em> from an <em>update</em>.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * public record BookDto(UUID id, String title) implements WithId {}
 *
 * BookDto fresh = new BookDto(null, "Effective Java");      // insert candidate
 * BookDto stored = new BookDto(UUID.randomUUID(), "...");   // update candidate
 * }</pre>
 */
public interface WithId {

  /**
   * Returns the unique identifier of this object, or {@code null} when the object has not been
   * persisted yet.
   *
   * @return the id, or {@code null} if not yet assigned
   */
  @Nullable UUID id();
}
