package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserService;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data service managing comments, setting the current user as the author of newly created comments.
 */
@Service
@Transactional
public class CommentService extends AbstractDbBackedDataService<CommentEntity, CommentDto> {

  private final CommentRepository commentRepository;

  private final UserService userService;

  /**
   * Creates a new comment service.
   *
   * @param commentRepository the repository used to persist and query comments
   * @param userService the service used to resolve the currently authenticated author
   * @param eventPublisher Spring's event publisher, passed to the superclass
   */
  public CommentService(
      @NonNull final CommentRepository commentRepository,
      @NonNull final UserService userService,
      @NonNull final ApplicationEventPublisher eventPublisher) {
    super(eventPublisher);
    this.commentRepository = Objects.requireNonNull(commentRepository);
    this.userService = Objects.requireNonNull(userService);
  }

  @Override
  protected @NonNull CommentEntity createDetachedEntity() {
    final UserEntity author = userService.getCurrentUserEntity();
    final CommentEntity entity = new CommentEntity();
    entity.setAuthor(author);
    return entity;
  }

  @Override
  protected void updateEntity(@NonNull final CommentEntity entity, @NonNull final CommentDto data) {
    entity.setText(data.text());
  }

  @Override
  protected @NonNull CommentDto toData(@NonNull final CommentEntity entity) {
    final UserDto author = UserDto.fromEntity(entity.getAuthor());
    return new CommentDto(
        entity.getId(), entity.getText(), author, entity.getCreatedAt(), entity.getUpdatedAt());
  }

  @Override
  protected @NonNull EntityRepository<CommentEntity> getRepository() {
    return commentRepository;
  }
}
