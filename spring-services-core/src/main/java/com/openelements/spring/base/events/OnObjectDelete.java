package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;

/**
 * Lifecycle event published after an entity has been deleted by a data service.
 *
 * <p>The event carries the DTO that <em>was</em> deleted, captured before removal, so that
 * listeners still have access to the full previous state (typically needed for cascade cleanup,
 * outbound notifications or audit logging).
 *
 * @param <T> the DTO type that was just deleted
 */
public class OnObjectDelete<T extends WithId> extends GenericDataEvent<T> {

  /**
   * Creates a new deletion event for the given DTO.
   *
   * @param source the DTO snapshot taken before the entity was removed
   */
  public OnObjectDelete(@NonNull T source) {
    super(source);
  }
}
