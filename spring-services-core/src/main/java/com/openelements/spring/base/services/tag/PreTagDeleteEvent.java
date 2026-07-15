package com.openelements.spring.base.services.tag;

import java.util.Objects;
import org.springframework.context.ApplicationEvent;

/**
 * Event published by {@link TagDataService} immediately before a tag is deleted.
 *
 * <p>Listeners receive the tag in its current state and may:
 *
 * <ul>
 *   <li>perform cleanup such as removing references to the tag from their own rows;
 *   <li>throw an exception to <em>veto</em> the deletion — the surrounding transaction is rolled
 *       back so the tag remains in the database.
 * </ul>
 *
 * <p>This is an <em>application</em> event (not a {@link
 * com.openelements.spring.base.events.GenericDataEvent}) because the veto semantics depend on
 * synchronous, in-transaction delivery before the row is gone.
 */
public class PreTagDeleteEvent extends ApplicationEvent {

  /**
   * Creates a new pre-delete event for the given tag.
   *
   * @param tag the tag that is about to be deleted
   * @throws NullPointerException if {@code tag} is {@code null}
   */
  public PreTagDeleteEvent(TagDto tag) {
    super(Objects.requireNonNull(tag, "tag must not be null"));
  }

  /**
   * Returns the tag that is about to be deleted.
   *
   * @return the tag in its pre-delete state
   */
  public TagDto getTag() {
    return (TagDto) getSource();
  }
}
