package com.openelements.spring.services.tag;

import com.openelements.spring.services.data.AbstractDbBackedDataService;
import com.openelements.spring.services.data.EntityRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class TagService extends AbstractDbBackedDataService<TagEntity, TagDto> {

    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TagService(final TagRepository tagRepository,
                      final ApplicationEventPublisher eventPublisher) {
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
