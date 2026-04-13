package com.openelements.spring.base.services.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new API key.
 *
 * @param name user-given name for the key
 */
@Schema(description = "Request body for creating a new API key")
public record ApiKeyCreateDto(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        @Schema(description = "Name for the API key", example = "CI Pipeline", requiredMode = Schema.RequiredMode.REQUIRED)
        String name
) {
}
