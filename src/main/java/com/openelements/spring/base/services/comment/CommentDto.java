package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.services.user.UserDto;
import java.time.Instant;
import java.util.UUID;

public record CommentDto(UUID id, String text, UserDto author, Instant createdAt, Instant updatedAt)
    implements WithId {}
