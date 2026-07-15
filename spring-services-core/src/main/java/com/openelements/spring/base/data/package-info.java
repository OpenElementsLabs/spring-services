/**
 * Foundational building blocks for persistent data services in the {@code spring-services}
 * platform.
 *
 * <h2>Overview</h2>
 *
 * <p>This package defines the core abstractions every data-driven service in the platform builds on
 * top of. It separates three responsibilities so they can evolve independently:
 *
 * <ul>
 *   <li><b>DTO layer</b> — immutable transport objects exposed by services and REST controllers.
 *       DTOs implement {@link com.openelements.spring.base.data.WithId} so the generic service
 *       contract can identify them by {@link java.util.UUID}.
 *   <li><b>Entity layer</b> — JPA-managed persistence objects that extend {@link
 *       com.openelements.spring.base.data.AbstractEntity}. The base class provides a generated
 *       {@code UUID} identifier as well as {@code createdAt} / {@code updatedAt} audit timestamps
 *       managed by Hibernate.
 *   <li><b>Service layer</b> — implementations of {@link
 *       com.openelements.spring.base.data.DataService} (and the database-backed extension {@link
 *       com.openelements.spring.base.data.DbBackedDataService}) that mediate between the two. The
 *       abstract template {@link com.openelements.spring.base.data.AbstractDbBackedDataService}
 *       wires CRUD, transactional boundaries, and lifecycle events together.
 * </ul>
 *
 * <h2>Lifecycle events</h2>
 *
 * <p>Every successful {@code save} or {@code delete} performed through {@link
 * com.openelements.spring.base.data.AbstractDbBackedDataService} publishes a Spring {@link
 * org.springframework.context.ApplicationEvent}:
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.events.OnObjectCreate} — published after a newly
 *       inserted entity has been persisted.
 *   <li>{@link com.openelements.spring.base.events.OnObjectUpdate} — published after an existing
 *       entity has been updated.
 *   <li>{@link com.openelements.spring.base.events.OnObjectDelete} — published after an entity has
 *       been removed.
 * </ul>
 *
 * <p>Subclasses can hook in by overriding the {@code preSave}, {@code postSave}, {@code preDelete}
 * and {@code postDelete} template methods.
 *
 * <h2>Example: implementing a data service for a {@code Book} domain object</h2>
 *
 * <p>The recommended layout is one entity, one repository, one DTO, and one service per domain
 * concept. The example below shows the minimum boilerplate required to participate in the
 * platform's data infrastructure.
 *
 * <h3>1. Entity</h3>
 *
 * <pre>{@code
 * @Entity
 * @Table(name = "books")
 * public class BookEntity extends AbstractEntity {
 *
 *   @Column(name = "title", nullable = false, length = 255)
 *   private String title;
 *
 *   @Column(name = "author", nullable = false, length = 255)
 *   private String author;
 *
 *   public String getTitle() { return title; }
 *   public void setTitle(String title) { this.title = title; }
 *
 *   public String getAuthor() { return author; }
 *   public void setAuthor(String author) { this.author = author; }
 * }
 * }</pre>
 *
 * <h3>2. Repository</h3>
 *
 * <pre>{@code
 * public interface BookRepository extends EntityRepository<BookEntity> {
 *   Optional<BookEntity> findByTitle(String title);
 * }
 * }</pre>
 *
 * <h3>3. DTO</h3>
 *
 * <p>The DTO is intentionally a plain transport object with no knowledge of the entity. The mapping
 * lives in the service (see {@code toData} below), so the API contract can evolve independently of
 * the persistence model.
 *
 * <pre>{@code
 * public record BookDto(UUID id, String title, String author) implements WithId {}
 * }</pre>
 *
 * <h3>4. Service</h3>
 *
 * <pre>{@code
 * @Service
 * public class BookDataService extends AbstractDbBackedDataService<BookEntity, BookDto> {
 *
 *   private final BookRepository repository;
 *
 *   public BookDataService(BookRepository repository, ApplicationEventPublisher eventPublisher) {
 *     super(eventPublisher);
 *     this.repository = repository;
 *   }
 *
 *   @Override
 *   protected BookEntity createDetachedEntity() {
 *     return new BookEntity();
 *   }
 *
 *   @Override
 *   protected void updateEntity(BookEntity entity, BookDto data) {
 *     entity.setTitle(data.title());
 *     entity.setAuthor(data.author());
 *   }
 *
 *   @Override
 *   protected BookDto toData(BookEntity entity) {
 *     return new BookDto(entity.getId(), entity.getTitle(), entity.getAuthor());
 *   }
 *
 *   @Override
 *   protected EntityRepository<BookEntity> getRepository() {
 *     return repository;
 *   }
 * }
 * }</pre>
 *
 * <h3>5. Calling the service</h3>
 *
 * <pre>{@code
 * BookDto created = bookDataService.save(new BookDto(null, "Effective Java", "Joshua Bloch"));
 * BookDto reloaded = bookDataService.findById(created.id()).orElseThrow();
 * Page<BookDto> page = bookDataService.findAll(PageRequest.of(0, 20));
 * bookDataService.delete(created.id());
 * }</pre>
 *
 * <h2>Tenant-aware variants</h2>
 *
 * <p>For multi-tenant services, see the {@code spring-services-tenant} module (package
 * {@code com.openelements.spring.base.tenant}), which provides the tenant-scoped counterparts of the
 * abstractions defined here.
 */
package com.openelements.spring.base.data;
