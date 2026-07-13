package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;

/**
 * Lifecycle event published after an existing entity has been updated by a data service.
 *
 * <p>The event carries the DTO in its <em>post-update</em> state, i.e. with the new field values
 * already applied. The previous state is not available — listeners that need to compute a diff have
 * to load it themselves.
 *
 * @param <T> the DTO type that was just updated
 */
public class OnObjectUpdate<T extends WithId> extends GenericDataEvent<T> {

  /**
   * @param source the updated DTO in its post-update state
   */
  public OnObjectUpdate(@NonNull T source) {
    super(source);
  }
}
