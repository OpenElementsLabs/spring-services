package com.openelements.spring.base.data;

import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class AbstractDbBackedDataService<E extends DbEntity, D extends WithId>
    implements DbBackedDataService<E, D> {

  private final ApplicationEventPublisher eventPublisher;

  public AbstractDbBackedDataService(@NonNull final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
  }

  @NonNull
  protected abstract E createDetachedEntity();

  protected abstract void updateEntity(@NonNull E entity, @NonNull D data);

  @NonNull
  protected abstract D toData(@NonNull E entity);

  @NonNull
  protected abstract EntityRepository<E> getRepository();

  protected void preSave(@NonNull final D data) {}

  protected void preDelete(@NonNull final D data) {}

  protected void postDelete(@NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    eventPublisher.publishEvent(new OnObjectDelete<>(data));
  }

  protected void postSave(final boolean insert, @NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    if (insert) {
      eventPublisher.publishEvent(new OnObjectCreate<>(data));
    } else {
      eventPublisher.publishEvent(new OnObjectUpdate<>(data));
    }
  }

  @NonNull
  @Override
  public D save(@NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    preSave(data);
    if (data.id() == null) {
      final E entity = createDetachedEntity();
      updateEntity(entity, data);
      final D saved = toData(getRepository().save(entity));
      postSave(true, saved);
      return saved;
    } else {
      final E entity =
          getRepository()
              .findById(data.id())
              .orElseThrow(
                  () -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
      updateEntity(entity, data);
      final D saved = toData(getRepository().save(entity));
      postSave(false, saved);
      return saved;
    }
  }

  @NonNull
  @Override
  public List<D> getAll() {
    return getRepository().findAll().stream().map(this::toData).toList();
  }

  @NonNull
  @Override
  public Optional<D> findById(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    return getRepository().findById(id).map(this::toData);
  }

  @Override
  public void delete(@NonNull final D data) {
    Objects.requireNonNull(data, "data must not be null");
    preDelete(data);
    final E entity =
        getRepository()
            .findById(data.id())
            .orElseThrow(
                () -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
    getRepository().delete(entity);
    postDelete(data);
  }

  @NonNull
  public Page<D> findAll(@NonNull final Pageable pageable) {
    Objects.requireNonNull(pageable, "pageable must not be null");
    final Page<E> page = getRepository().findAll(pageable);
    return page.map(e -> toData(e));
  }

  public boolean existsById(@NonNull final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    return getRepository().existsById(id);
  }
}
