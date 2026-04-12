package com.openelements.spring.services.webhook;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a webhook registration.
 */
@Schema(description = "Webhook")
public record WebhookDto(
        @Schema(description = "Webhook ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(description = "Target URL", requiredMode = Schema.RequiredMode.REQUIRED) String url,
        @Schema(description = "Whether the webhook is active", requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(description = "Last HTTP response status (null=never called, 0=connection error, -1=timeout)") Integer lastStatus,
        @Schema(description = "Timestamp of the last webhook call") Instant lastCalledAt,
        @Schema(description = "Creation timestamp", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(description = "Last update timestamp", requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {

    public static WebhookDto fromEntity(final WebhookEntity entity) {
        return new WebhookDto(
                entity.getId(),
                entity.getUrl(),
                entity.isActive(),
                entity.getLastStatus(),
                entity.getLastCalledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
