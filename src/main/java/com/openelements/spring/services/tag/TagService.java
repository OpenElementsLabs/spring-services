package com.openelements.spring.services.tag;

import com.openelements.spring.services.events.OnObjectCreate;
import com.openelements.spring.services.events.OnObjectDelete;
import com.openelements.spring.services.events.OnObjectUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TagService(final TagRepository tagRepository,
                      final ApplicationEventPublisher eventPublisher) {
        this.tagRepository = Objects.requireNonNull(tagRepository, "tagRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    public Page<TagDto> findAll(final Pageable pageable) {
        final Page<TagEntity> page = tagRepository.findAll(pageable);
        return page.map(TagDto::fromEntity);
    }

    public TagDto findById(final UUID id) {
        return tagRepository.findById(id)
                .map(TagDto::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
    }

    public TagDto create(final TagCreateDto dto) {
        if (tagRepository.existsByNameIgnoreCase(dto.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag with this name already exists");
        }
        final TagEntity entity = new TagEntity();
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setColor(dto.color());
        final TagDto result = TagDto.fromEntity(tagRepository.saveAndFlush(entity));
        eventPublisher.publishEvent(new OnObjectCreate<>(result));
        return result;
    }

    public TagDto update(final UUID id, final TagCreateDto dto) {
        final TagEntity entity = tagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
        tagRepository.findByNameIgnoreCase(dto.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag with this name already exists");
                });
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setColor(dto.color());
        final TagDto result = TagDto.fromEntity(tagRepository.saveAndFlush(entity));
        eventPublisher.publishEvent(new OnObjectUpdate<>(result));
        return result;
    }

    public void delete(final UUID id) {
        final TagEntity tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
        final TagDto dto = TagDto.fromEntity(tag);
        eventPublisher.publishEvent(new PreTagDeleteEvent(dto));
        tagRepository.delete(tag);
        eventPublisher.publishEvent(new OnObjectDelete<>(dto));
    }

    public Set<TagEntity> resolveTagIds(final List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new HashSet<>();
        }
        final List<TagEntity> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more tag IDs do not exist");
        }
        return new HashSet<>(tags);
    }
}
