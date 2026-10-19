package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Lifecycle event published after an existing entity has been updated.
 *
 * <p>The event always carries the DTO in its <em>post-update</em> state ({@link #getData()}).
 * Whether the state <em>before</em> the update is available depends on the publisher:
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.data.AbstractDbBackedDataService} captures it for every
 *       update and publishes the event only if something actually changed.
 *   <li>Publishers that cannot know the previous state — for example because the data comes from
 *       an external system — use the one-argument constructor. {@link #isPreviousKnown()} is then
 *       {@code false}.
 * </ul>
 *
 * <p>The event does not compute a diff. Listeners that care about individual properties compare
 * {@link #getPrevious()} with {@link #getData()} themselves:
 *
 * <pre>{@code
 * @EventListener
 * public void onBookUpdated(OnObjectUpdate<BookDto> event) {
 *   event.getPrevious()
 *       .filter(previous -> !previous.title().equals(event.getData().title()))
 *       .ifPresent(previous -> notifyTitleChanged(previous.title(), event.getData().title()));
 * }
 * }</pre>
 *
 * <p>Both DTOs are snapshots: they must not share mutable state (such as a collection) with the
 * entity they were created from, otherwise the previous state would change along with the entity.
 *
 * @param <T> the DTO type that was just updated
 */
public class OnObjectUpdate<T extends WithId> extends GenericDataEvent<T> {

  /** The state before the update, or {@code null} if the publisher does not know it. */
  @Nullable private final T previous;

  /**
   * Creates a new update event whose previous state is unknown.
   *
   * @param current the updated DTO in its post-update state
   */
  public OnObjectUpdate(@NonNull final T current) {
    super(current);
    this.previous = null;
  }

  /**
   * Creates a new update event that carries the state before and after the update.
   *
   * @param previous the DTO in its pre-update state
   * @param current the updated DTO in its post-update state
   * @throws NullPointerException if {@code previous} or {@code current} is {@code null}
   * @throws IllegalArgumentException if both DTOs are of different classes or carry different ids
   */
  public OnObjectUpdate(@NonNull final T previous, @NonNull final T current) {
    super(current);
    Objects.requireNonNull(previous, "previous must not be null");
    if (!previous.getClass().equals(current.getClass())) {
      throw new IllegalArgumentException(
          "previous is a "
              + previous.getClass().getName()
              + " but current is a "
              + current.getClass().getName());
    }
    if (!Objects.equals(previous.id(), current.id())) {
      throw new IllegalArgumentException(
          "previous has id " + previous.id() + " but current has id " + current.id());
    }
    this.previous = previous;
  }

  /**
   * Returns whether the publisher supplied the state before the update.
   *
   * @return {@code true} if {@link #getPrevious()} is present
   */
  public boolean isPreviousKnown() {
    return previous != null;
  }

  /**
   * Returns the DTO in its pre-update state, if the publisher knows it.
   *
   * @return the previous state, or an empty {@link Optional} if it is unknown
   */
  @NonNull
  public Optional<T> getPrevious() {
    return Optional.ofNullable(previous);
  }
}
