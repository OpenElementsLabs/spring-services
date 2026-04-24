package com.openelements.spring.base.data;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.NonNull;
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

  @NonNull
  static <T extends WithId> Set<UUID> toIdSet(@NonNull final Iterable<T> data) {
    return toIdStream(data).collect(Collectors.toUnmodifiableSet());
  }

  @NonNull
  static <T extends WithId> List<UUID> toIdList(@NonNull final Iterable<T> data) {
    return toIdStream(data).toList();
  }

  @NonNull
  static <T extends WithId> Stream<UUID> toIdStream(@NonNull final Iterable<T> data) {
    Objects.requireNonNull(data, "data must not be null");
    return StreamSupport.stream(data.spliterator(), false).map(d -> d.id());
  }

  /**
   * Returns the unique identifier of this object, or {@code null} when the object has not been
   * persisted yet.
   *
   * @return the id, or {@code null} if not yet assigned
   */
  @Nullable UUID id();
}
