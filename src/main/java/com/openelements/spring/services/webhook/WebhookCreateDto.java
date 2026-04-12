package com.openelements.spring.services.webhook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new webhook.
 *
 * @param url the target URL (required)
 */
@Schema(description = "Request body for creating a new webhook")
public record WebhookCreateDto(
        @NotBlank(message = "URL must not be blank")
        @Size(max = 2048)
        @Schema(description = "Target URL", example = "https://example.com/webhook", requiredMode = Schema.RequiredMode.REQUIRED)
        String url
) {
}
