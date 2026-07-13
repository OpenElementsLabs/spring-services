/**
 * Domain lifecycle events published by the data services of the {@code spring-services} platform.
 *
 * <p>Every successful create, update or delete performed through {@link
 * com.openelements.spring.base.data.AbstractDbBackedDataService} broadcasts a Spring {@link
 * org.springframework.context.ApplicationEvent} so that other components — projection updaters,
 * webhook dispatchers, audit loggers, search indexers — can react to data changes without coupling
 * themselves to the originating service.
 *
 * <h2>Event types</h2>
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.events.OnObjectCreate} — published after a new entity
 *       has been inserted.
 *   <li>{@link com.openelements.spring.base.events.OnObjectUpdate} — published after an existing
 *       entity has been updated.
 *   <li>{@link com.openelements.spring.base.events.OnObjectDelete} — published after an entity has
 *       been deleted.
 * </ul>
 *
 * All three extend the common base class {@link
 * com.openelements.spring.base.events.GenericDataEvent}, which carries the affected DTO as the
 * event source and exposes its concrete type and id for type-safe filtering.
 *
 * <h2>Listening for events</h2>
 *
 * Listeners are written using Spring's standard {@link
 * org.springframework.context.event.EventListener} support. The generic parameter on the event type
 * allows narrowing to a single domain concept:
 *
 * <pre>{@code
 * @Component
 * public class BookSearchIndexer {
 *
 *   @EventListener
 *   public void onBookCreated(OnObjectCreate<BookDto> event) {
 *     BookDto book = event.getData();
 *     index(book);
 *   }
 *
 *   @EventListener
 *   public void onBookDeleted(OnObjectDelete<BookDto> event) {
 *     removeFromIndex(event.entityId());
 *   }
 * }
 * }</pre>
 *
 * <p>To react to <em>any</em> data change regardless of type, listen to {@link
 * com.openelements.spring.base.events.GenericDataEvent} directly and inspect {@link
 * com.openelements.spring.base.events.GenericDataEvent#getType()}.
 *
 * <h2>Threading and transactions</h2>
 *
 * Events are published synchronously from inside the originating service's {@link
 * org.springframework.transaction.annotation.Transactional} boundary — listeners therefore see
 * changes as part of the same transaction. Annotate listeners with {@link
 * org.springframework.transaction.event.TransactionalEventListener} when they should only run after
 * the transaction has been committed (e.g. when triggering external side effects such as sending a
 * webhook).
 */
package com.openelements.spring.base.events;
