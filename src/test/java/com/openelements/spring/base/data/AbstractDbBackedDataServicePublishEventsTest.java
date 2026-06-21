package com.openelements.spring.base.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import jakarta.persistence.Entity;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * during {@code save(...)} and {@code delete(...)}. Two scenarios are verified: default
 * behaviour (events are emitted) and opt-out behaviour (no events at all).
 *
 * <h2>How it is tested</h2>
 *
 * <p>Mockito-based unit test. Two collaborators are mocked: {@link ApplicationEventPublisher}
 * (verified via {@code verify(publisher).publishEvent(any(...))}) and {@link EntityRepository}
 * (stubbed to assign a UUID on first save and echo the entity back on subsequent calls — the
 * helper {@code repositoryThatPersists()} encapsulates this in-memory pseudo-persistence).
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
    final EntityRepository<TestEntity> repository = mock(EntityRepository.class);
    when(repository.save(any(TestEntity.class)))
        .thenAnswer(
            invocation -> {
              final TestEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
              }
              return entity;
            });
    when(repository.findById(any(UUID.class)))
        .thenAnswer(
            invocation -> {
              final TestEntity entity = new TestEntity();
              entity.setId(invocation.getArgument(0));
              return Optional.of(entity);
            });
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
    final TestDto saved = service.save(new TestDto(null));
    service.save(new TestDto(saved.id()));
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
    final TestDto saved = service.save(new TestDto(null));
    service.save(new TestDto(saved.id()));
    service.delete(saved);

    // THEN
    verify(publisher, never()).publishEvent(any());
    assertThat(saved.id()).isNotNull();
  }

  @Entity
  static class TestEntity extends AbstractEntity {}

  record TestDto(UUID id) implements WithId {}

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
    protected void updateEntity(@NonNull final TestEntity entity, @NonNull final TestDto data) {}

    @Override
    protected @NonNull TestDto toData(@NonNull final TestEntity entity) {
      return new TestDto(entity.getId());
    }

    @Override
    protected @NonNull EntityRepository<TestEntity> getRepository() {
      return repository;
    }
  }
}
