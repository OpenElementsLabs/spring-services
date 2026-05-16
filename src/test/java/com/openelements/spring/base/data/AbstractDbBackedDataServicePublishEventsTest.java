package com.openelements.spring.base.data;

import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.services.audit.AbstractEntity;
import jakarta.persistence.Entity;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    static class TestEntity extends AbstractEntity {
    }

    record TestDto(UUID id) implements WithId {
    }

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
        }

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
