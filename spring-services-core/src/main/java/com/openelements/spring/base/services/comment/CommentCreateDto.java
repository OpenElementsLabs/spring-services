package com.openelements.spring.base.services.comment;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO carrying the text of a comment to be created.
 *
 * @param text the comment text; must not be blank
 */
public record CommentCreateDto(@NotBlank String text) {}
