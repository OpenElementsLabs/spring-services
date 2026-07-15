package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD service for {@link TagDto}.
 *
 * <p>In addition to the standard {@link com.openelements.spring.base.events.OnObjectCreate} /
 * {@code OnObjectUpdate} / {@code OnObjectDelete} lifecycle events inherited from {@link
 * AbstractDbBackedDataService}, this service publishes a {@link PreTagDeleteEvent} immediately
 * before a tag is deleted. Other features that hold references to tags can listen to this event to
 * detach those references — or to veto the deletion by throwing.
 */
@Service
@Transactional
public class TagDataService extends AbstractDbBackedDataService<TagEntity, TagDto> {

  private final TagRepository tagRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Creates a new tag service.
   *
   * @param tagRepository the repository used to persist and query tags
   * @param eventPublisher Spring's event publisher, used to broadcast lifecycle events
   */
  public TagDataService(
      final TagRepository tagRepository, final ApplicationEventPublisher eventPublisher) {
    super(eventPublisher);
    this.tagRepository = Objects.requireNonNull(tagRepository, "tagRepository must not be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
  }

  @Override
  protected TagEntity createDetachedEntity() {
    return new TagEntity();
  }

  @Override
  protected void updateEntity(TagEntity entity, TagDto data) {
    entity.setName(data.name());
    entity.setDescription(data.description());
    entity.setColor(data.color());
  }

  @Override
  protected TagDto toData(TagEntity entity) {
    return new TagDto(entity.getId(), entity.getName(), entity.getDescription(), entity.getColor());
  }

  @Override
  protected EntityRepository<TagEntity> getRepository() {
    return tagRepository;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Publishes a {@link PreTagDeleteEvent} so that other features can react to (or veto) the
   * deletion before it happens.
   */
  @Override
  protected void preDelete(TagDto data) {
    eventPublisher.publishEvent(new PreTagDeleteEvent(data));
  }
}
