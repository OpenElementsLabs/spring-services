package com.openelements.spring.base.data;

import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ForTestDataService extends AbstractDbBackedDataService<ForTestEntity, ForTestDto> {

    private final ForTestRepository repository;

    public ForTestDataService(@NonNull ApplicationEventPublisher eventPublisher, ForTestRepository repository) {
        super(eventPublisher);
        this.repository = repository;
    }

    @Override
    protected @NonNull ForTestEntity createDetachedEntity() {
        return new ForTestEntity();
    }

    @Override
    protected void updateEntity(@NonNull ForTestEntity entity, @NonNull ForTestDto data) {
        entity.setName(data.name());
    }

    @Override
    protected @NonNull ForTestDto toData(@NonNull ForTestEntity entity) {
        return new ForTestDto(entity.getId(), entity.getName());
    }

    @Override
    protected @NonNull EntityRepository<ForTestEntity> getRepository() {
        return repository;
    }
}
