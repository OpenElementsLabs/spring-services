package com.openelements.spring.base.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import jakarta.persistence.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for the {@code publishEvents} flag on {@link AbstractDbBackedDataService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The flag controls whether the base class emits {@link
 * com.openelements.spring.base.events.OnObjectCreate}, {@link
 * com.openelements.spring.base.events.OnObjectUpdate} and {@link
 * com.openelements.spring.base.events.OnObjectDelete} on the {@link ApplicationEventPublisher}
 * during {@code save(...)} and {@code delete(...)}. Verified are: default behaviour (events are
 * emitted), opt-out behaviour (no events at all), an update carrying the state before and after,
 * and an update that changes nothing (no event).
 *
 * <h2>How it is tested</h2>
 *
 * <p>Mockito-based unit test. Two collaborators are mocked: {@link ApplicationEventPublisher}
 * (verified via {@code verify(publisher).publishEvent(any(...))}) and {@link EntityRepository}
 * (backed by an in-memory map that assigns a UUID on first save and hands out the same managed
 * instance on {@code findById} — the helper {@code repositoryThatPersists()} encapsulates this
 * pseudo-persistence, so {@code updateEntity} mutates the instance the previous state was taken
 * from, exactly like a JPA persistence context).
 *
 * <p>An anonymous {@code FakeService extends AbstractDbBackedDataService} stands in for any
 * real subclass so the test does not couple to a domain entity. The flag itself is the unit
 * under test, not the surrounding template-method machinery.
 *
 * <p><b>Mock-Audit.</b> Both mocks are justified: the publisher mock is the assertion target;
 * the repository mock substitutes for a JPA layer that would require Spring + Testcontainers
 * to use for real, which would dwarf the focused scope of the test.
 */
@DisplayName("AbstractDbBackedDataService publishEvents flag")
class AbstractDbBackedDataServicePublishEventsTest {

  @SuppressWarnings("unchecked")
  private static EntityRepository<TestEntity> repositoryThatPersists() {
    final Map<UUID, TestEntity> store = new HashMap<>();
    final EntityRepository<TestEntity> repository = mock(EntityRepository.class);
    when(repository.save(any(TestEntity.class)))
        .thenAnswer(
            invocation -> {
              final TestEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
              }
              store.put(entity.getId(), entity);
              return entity;
            });
    when(repository.findById(any(UUID.class)))
        .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
    return repository;
  }

  @Test
  @DisplayName(
      "With the default constructor (publishEvents not specified), save/save/delete emits "
          + "OnObjectCreate, OnObjectUpdate, OnObjectDelete to the ApplicationEventPublisher.")
  void shouldPublishEventsByDefault() {
    // GIVEN
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final FakeService service = new FakeService(publisher, repositoryThatPersists());

    // WHEN
    final TestDto saved = service.save(new TestDto(null, "old"));
    service.save(new TestDto(saved.id(), "new"));
    service.delete(saved);

    // THEN
    verify(publisher).publishEvent(any(OnObjectCreate.class));
    verify(publisher).publishEvent(any(OnObjectUpdate.class));
    verify(publisher).publishEvent(any(OnObjectDelete.class));
  }

  @Test
  @DisplayName(
      "With publishEvents=false the ApplicationEventPublisher receives zero events even though "
          + "save() and delete() succeed and the entity gets its id assigned.")
  void shouldSuppressEventsWhenFlagIsFalse() {
    // GIVEN
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final FakeService service = new FakeService(publisher, false, repositoryThatPersists());

    // WHEN
    final TestDto saved = service.save(new TestDto(null, "old"));
    service.save(new TestDto(saved.id(), "new"));
    service.delete(saved);

    // THEN
    verify(publisher, never()).publishEvent(any());
    assertThat(saved.id()).isNotNull();
  }

  @Test
  @DisplayName(
      "An update publishes one OnObjectUpdate whose previous state is the DTO before the update "
          + "and whose data is the DTO after it.")
  @SuppressWarnings("unchecked")
  void shouldPublishPreviousAndCurrentStateOnUpdate() {
    // GIVEN
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final FakeService service = new FakeService(publisher, repositoryThatPersists());
    final TestDto saved = service.save(new TestDto(null, "old"));
    clearInvocations(publisher);

    // WHEN
    service.save(new TestDto(saved.id(), "new"));

    // THEN
    final ArgumentCaptor<OnObjectUpdate<TestDto>> captor =
        ArgumentCaptor.forClass(OnObjectUpdate.class);
    verify(publisher).publishEvent(captor.capture());
    final OnObjectUpdate<TestDto> event = captor.getValue();
    assertThat(event.getPrevious()).contains(new TestDto(saved.id(), "old"));
    assertThat(event.getData()).isEqualTo(new TestDto(saved.id(), "new"));
  }

  @Test
  @DisplayName(
      "An update that changes nothing publishes no event but still returns the saved DTO.")
  void shouldNotPublishOnNoOpUpdate() {
    // GIVEN
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final FakeService service = new FakeService(publisher, repositoryThatPersists());
    final TestDto saved = service.save(new TestDto(null, "same"));
    clearInvocations(publisher);

    // WHEN
    final TestDto result = service.save(new TestDto(saved.id(), "same"));

    // THEN
    verify(publisher, never()).publishEvent(any());
    assertThat(result).isEqualTo(saved);
  }

  @Entity
  static class TestEntity extends AbstractEntity {

    private String name;
  }

  record TestDto(UUID id, String name) implements WithId {}

  private static class FakeService extends AbstractDbBackedDataService<TestEntity, TestDto> {

    private final EntityRepository<TestEntity> repository;

    FakeService(
        @NonNull final ApplicationEventPublisher publisher,
        final boolean publishEvents,
        @NonNull final EntityRepository<TestEntity> repository) {
      super(publisher, publishEvents);
      this.repository = repository;
    }

    FakeService(
        @NonNull final ApplicationEventPublisher publisher,
        @NonNull final EntityRepository<TestEntity> repository) {
      super(publisher);
      this.repository = repository;
    }

    @Override
    protected @NonNull TestEntity createDetachedEntity() {
      return new TestEntity();
    }

    @Override
    protected void updateEntity(@NonNull final TestEntity entity, @NonNull final TestDto data) {
      entity.name = data.name();
    }

    @Override
    protected @NonNull TestDto toData(@NonNull final TestEntity entity) {
      return new TestDto(entity.getId(), entity.name);
    }

    @Override
    protected @NonNull EntityRepository<TestEntity> getRepository() {
      return repository;
    }
  }
}
