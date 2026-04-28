package com.openelements.spring.base.services.comment;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateDto(@NotBlank String text) {
}
