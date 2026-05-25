package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

/**
 * Base class for all lifecycle events fired by the platform's data services.
 *
 * <p>The event source is the affected DTO ({@link WithId}); the concrete DTO type is captured
 * separately so that listeners can perform type-safe filtering even when the source is referenced
 * through a generic {@code GenericDataEvent<?>}. This is necessary because Java erases the type
 * parameter at runtime — without an explicit class reference there is no way to ask "is this an
 * event about a {@code BookDto}?".
 *
 * <p>Application code should normally not extend this class directly — use one of the concrete
 * subtypes {@link OnObjectCreate}, {@link OnObjectUpdate} or {@link OnObjectDelete} so that
 * listeners can distinguish the kind of change that occurred.
 *
 * @param <T> the DTO type this event refers to
 */
public abstract class GenericDataEvent<T extends WithId> extends ApplicationEvent {

  private final Class<T> type;

  /**
   * Creates a new event whose DTO type is inferred from the runtime class of {@code source}.
   *
   * @param source the DTO that was just created, updated or deleted
   * @throws NullPointerException if {@code source} is {@code null}
   */
  public GenericDataEvent(@NonNull final T source) {
    super(Objects.requireNonNull(source, "source must not be null"));
    this.type = (Class<T>) source.getClass();
  }

  /**
   * Returns the affected DTO.
   *
   * <p>Overrides {@link ApplicationEvent#getSource()} to return the typed DTO instead of {@link
   * Object}.
   *
   * @return the affected DTO
   */
  @Nullable
  public T getSource() {
    return (T) super.getSource();
  }

  /**
   * Convenience alias for {@link #getSource()} that reads more naturally in listener code.
   *
   * @return the affected DTO
   */
  @Nullable
  public T getData() {
    return getSource();
  }

  /**
   * Returns the runtime DTO type this event was published for. Useful for filtering when listening
   * to {@code GenericDataEvent<?>} events generically.
   *
   * @return the DTO type
   */
  @NonNull
  public Class<T> getType() {
    return type;
  }

  /**
   * Returns the id of the affected DTO.
   *
   * @return the entity id
   */
  @NonNull
  public UUID entityId() {
    return getData().id();
  }
}
