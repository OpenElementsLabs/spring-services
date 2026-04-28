package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import com.openelements.spring.base.security.user.UserDto;
import com.openelements.spring.base.security.user.UserService;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class CommentService extends AbstractDbBackedDataService<CommentEntity, CommentDto> {

    private final CommentRepository commentRepository;

    private final UserService userService;

    public CommentService(@NonNull final CommentRepository commentRepository,
                          @NonNull final UserService userService,
                          @NonNull final ApplicationEventPublisher eventPublisher) {
        super(eventPublisher);
        this.commentRepository = Objects.requireNonNull(commentRepository);
        this.userService = Objects.requireNonNull(userService);
    }

    @Override
    protected @NonNull CommentEntity createDetachedEntity() {
        final UserDto user = userService.getCurrentUser();
        final CommentEntity entity = new CommentEntity();
        entity.setAuthorId(user.id().toString());
        return entity;
    }

    @Override
    protected void updateEntity(@NonNull final CommentEntity entity, @NonNull final CommentDto data) {
        entity.setText(data.text());
    }

    @Override
    protected @NonNull CommentDto toData(@NonNull final CommentEntity entity) {
        final UserDto author = userService.findById(entity.getAuthorId()).orElseThrow();
        return new CommentDto(entity.getId(), entity.getText(), author,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    protected @NonNull EntityRepository<CommentEntity> getRepository() {
        return commentRepository;
    }
}
