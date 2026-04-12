package com.openelements.spring.services.webhook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating an existing webhook.
 *
 * @param url    the target URL (required)
 * @param active whether the webhook is active
 */
@Schema(description = "Request body for updating a webhook")
public record WebhookUpdateDto(
        @NotBlank(message = "URL must not be blank")
        @Size(max = 2048)
        @Schema(description = "Target URL", example = "https://example.com/webhook", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,

        @NotNull(message = "Active flag must not be null")
        @Schema(description = "Whether the webhook is active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean active
) {
}
