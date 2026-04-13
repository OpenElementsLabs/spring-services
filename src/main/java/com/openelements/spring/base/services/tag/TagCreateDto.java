package com.openelements.spring.base.services.tag;

import jakarta.validation.constraints.NotBlank;

public record TagCreateDto(@NotBlank String name, String description, @NotBlank String color) {
}
