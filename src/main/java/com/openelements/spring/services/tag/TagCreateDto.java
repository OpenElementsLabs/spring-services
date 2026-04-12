package com.openelements.spring.services.tag;

import jakarta.validation.constraints.NotBlank;

public record TagCreateDto(@NotBlank String name, String description, @NotBlank String color) {
}
