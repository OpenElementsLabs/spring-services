package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;

/**
 * Lifecycle event published after a new entity has been inserted by a data service.
 *
 * <p>See the {@linkplain com.openelements.spring.base.events package documentation} for an example
 * showing how listeners react to creation events.
 *
 * @param <T> the DTO type that was just created
 */
public class OnObjectCreate<T extends WithId> extends GenericDataEvent<T> {

  /**
   * Creates a new creation event for the given DTO.
   *
   * @param source the newly created DTO
   */
  public OnObjectCreate(@NonNull T source) {
    super(source);
  }
}
