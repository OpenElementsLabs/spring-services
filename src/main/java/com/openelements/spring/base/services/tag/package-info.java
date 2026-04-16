/**
 * Generic tag store that other features can attach to their domain objects.
 *
 * <p>A tag is a small, named, color-coded label with an optional description. Tags are stored as
 * first-class entities in the {@code tags} table and managed through {@link
 * com.openelements.spring.base.services.tag.TagDataService} (full CRUD + pagination via {@link
 * com.openelements.spring.base.data.AbstractDbBackedDataService}).
 *
 * <h2>Cross-feature integration</h2>
 *
 * <p>Other features that hold references to tags can react to deletions by listening to the {@link
 * com.openelements.spring.base.services.tag.PreTagDeleteEvent}. The event is published
 * <em>before</em> the row is removed, so listeners get a chance to reject the deletion (by
 * throwing) or to detach the tag from their own rows. Standard {@link
 * com.openelements.spring.base.events.OnObjectCreate}, {@code OnObjectUpdate} and {@code
 * OnObjectDelete} events are also published as for any other data service.
 */
package com.openelements.spring.base.services.tag;
