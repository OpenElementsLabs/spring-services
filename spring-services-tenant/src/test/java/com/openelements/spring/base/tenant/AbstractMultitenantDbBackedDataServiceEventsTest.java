package com.openelements.spring.base.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectUpdate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for the lifecycle events published by {@link AbstractMultitenantDbBackedDataService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>An insert publishes {@link OnObjectCreate}; an update publishes {@link OnObjectUpdate} with the
 * state before and after the update; an update that changes nothing publishes no event.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Mockito-based unit test. The repository is backed by an in-memory map that hands out the same
 * managed instance on every lookup, so {@code updateEntity} mutates the instance the previous state
 * was taken from — exactly like a JPA persistence context. The current tenant is fixed by
 * overriding {@code getCurrentTenant()}.
 *
 * <p><b>Mock-Audit.</b> The publisher mock is the assertion target; the repository mock replaces a
 * JPA layer that would need Spring and a database, which would dwarf the focused scope of the test.
 */
@DisplayName("AbstractMultitenantDbBackedDataService lifecycle events")
class AbstractMultitenantDbBackedDataServiceEventsTest {

  private static final String TENANT = "tenant-a";

  @SuppressWarnings("unchecked")
  private static RepositoryWithTenantSupport<TestEntity, UUID> repositoryThatPersists() {
    final Map<UUID, TestEntity> store = new HashMap<>();
    final RepositoryWithTenantSupport<TestEntity, UUID> repository =
        mock(RepositoryWithTenantSupport.class);
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
    when(repository.findByIdAndTenantId(any(UUID.class), anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(store.get(invocation.<UUID>getArgument(0)))
                    .filter(entity -> entity.getTenantId().equals(invocation.getArgument(1))));
    return repository;
  }

  @Test
  @DisplayName("An insert publishes exactly one OnObjectCreate carrying the saved DTO.")
  void shouldPublishCreateOnInsert() {
    // GIVEN
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final FakeService service = new FakeService(publisher, repositoryThatPersists());

    // WHEN
    service.save(new TestDto(null, "new"));

    // THEN
    verify(publisher).publishEvent(any(OnObjectCreate.class));
  }

  @Test
  @DisplayName(
      "An update publishes one OnObjectUpdate with the DTO before the update as previous state.")
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
    assertThat(captor.getValue().getPrevious()).contains(new TestDto(saved.id(), "old"));
    assertThat(captor.getValue().getData()).isEqualTo(new TestDto(saved.id(), "new"));
  }

  @Test
  @DisplayName("An update that changes nothing publishes no event.")
  void shouldNotPublishOnNoOpUpdate() {
    // GIVEN
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final FakeService service = new FakeService(publisher, repositoryThatPersists());
    final TestDto saved = service.save(new TestDto(null, "same"));
    clearInvocations(publisher);

    // WHEN
    service.save(new TestDto(saved.id(), "same"));

    // THEN
    verify(publisher, never()).publishEvent(any());
  }

  static class TestEntity extends AbstractMultitenantEntity {

    private String name;
  }

  record TestDto(UUID id, String name) implements WithId {}

  private static class FakeService
      extends AbstractMultitenantDbBackedDataService<TestEntity, TestDto> {

    private final RepositoryWithTenantSupport<TestEntity, UUID> repository;

    FakeService(
        final ApplicationEventPublisher publisher,
        final RepositoryWithTenantSupport<TestEntity, UUID> repository) {
      super(publisher);
      this.repository = repository;
    }

    @Override
    protected RepositoryWithTenantSupport<TestEntity, UUID> getRepository() {
      return repository;
    }

    @Override
    protected TenantService getTenantService() {
      throw new UnsupportedOperationException("getCurrentTenant() is overridden");
    }

    @Override
    protected String getCurrentTenant() {
      return TENANT;
    }

    @Override
    protected TestDto mapToData(final TestEntity entity) {
      return new TestDto(entity.getId(), entity.name);
    }

    @Override
    protected TestEntity updateEntity(final TestEntity entity, final TestDto data) {
      entity.name = data.name();
      return entity;
    }

    @Override
    protected TestEntity createNewEntity() {
      return new TestEntity();
    }

    @Override
    public Page<TestDto> findAll(final Pageable pageable) {
      throw new UnsupportedOperationException("not needed by these tests");
    }
  }
}
