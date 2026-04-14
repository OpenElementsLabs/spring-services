package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagDataService extends AbstractDbBackedDataService<TagEntity, TagDto> {

  private final TagRepository tagRepository;
  private final ApplicationEventPublisher eventPublisher;

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

  @Override
  protected void preDelete(TagDto data) {
    eventPublisher.publishEvent(new PreTagDeleteEvent(data));
  }
}
