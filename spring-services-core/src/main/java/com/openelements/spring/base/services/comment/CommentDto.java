package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.services.user.UserDto;
import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a comment.
 *
 * @param id the unique identifier of the comment
 * @param text the comment text
 * @param author the user who wrote the comment
 * @param createdAt the timestamp at which the comment was created
 * @param updatedAt the timestamp at which the comment was last updated
 */
public record CommentDto(UUID id, String text, UserDto author, Instant createdAt, Instant updatedAt)
    implements WithId {}
