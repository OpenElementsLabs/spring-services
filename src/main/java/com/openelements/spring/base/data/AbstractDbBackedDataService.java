package com.openelements.spring.base.data;

import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class AbstractDbBackedDataService<E extends DbEntity, D extends WithId>
    implements DbBackedDataService<E, D> {

  private final ApplicationEventPublisher eventPublisher;

  public AbstractDbBackedDataService(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
  }

  protected abstract E createDetachedEntity();

  protected abstract void updateEntity(E entity, D data);

  protected abstract D toData(E entity);

  protected abstract EntityRepository<E> getRepository();

  protected void preSave(final D data) {}

  protected void preDelete(final D data) {}

  protected void postDelete(D data) {
    eventPublisher.publishEvent(new OnObjectDelete<>(data));
  }

  protected void postSave(final boolean insert, D data) {
    if (insert) {
      eventPublisher.publishEvent(new OnObjectCreate<>(data));
    } else {
      eventPublisher.publishEvent(new OnObjectUpdate<>(data));
    }
  }

  @Override
  public D save(final D data) {
    preSave(data);
    if (data.id() != null) {
      final E entity = createDetachedEntity();
      updateEntity(entity, data);
      D saved = toData(getRepository().save(entity));
      postSave(true, saved);
      return saved;
    } else {
      final E entity =
          getRepository()
              .findById(data.id())
              .orElseThrow(
                  () -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
      updateEntity(entity, data);
      D saved = toData(getRepository().save(entity));
      postSave(false, saved);
      return saved;
    }
  }

  @Override
  public List<D> getAll() {
    return getRepository().findAll().stream().map(this::toData).toList();
  }

  @Override
  public Optional<D> findById(final UUID id) {
    return getRepository().findById(id).map(this::toData);
  }

  @Override
  public void delete(final D data) {
    preDelete(data);
    E entity =
        getRepository()
            .findById(data.id())
            .orElseThrow(
                () -> new IllegalArgumentException("Entity with id " + data.id() + " not found"));
    getRepository().delete(entity);
    postDelete(data);
  }

  public Page<D> findAll(final Pageable pageable) {
    final Page<E> page = getRepository().findAll(pageable);
    return page.map(e -> toData(e));
  }
}
