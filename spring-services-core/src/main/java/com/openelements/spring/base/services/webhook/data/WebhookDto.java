package com.openelements.spring.base.services.webhook.data;

import com.openelements.spring.base.data.WithId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a webhook registration.
 *
 * @param id the unique identifier of the webhook registration
 * @param url the target URL that receives the webhook callbacks
 * @param active whether the webhook is currently enabled and will be called
 * @param lastStatus the HTTP status of the last delivery attempt ({@code null} if never called,
 *     {@code 0} for a connection error, {@code -1} for a timeout)
 * @param lastCalledAt the timestamp of the most recent delivery attempt, or {@code null} if never
 *     called
 */
@Schema(description = "Webhook")
public record WebhookDto(
    @Schema(description = "Webhook ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "Target URL", requiredMode = Schema.RequiredMode.REQUIRED) String url,
    @Schema(
            description = "Whether the webhook is active",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
    @Schema(
            description =
                "Last HTTP response status (null=never called, 0=connection error, -1=timeout)")
        Integer lastStatus,
    @Schema(description = "Timestamp of the last webhook call") Instant lastCalledAt)
    implements WithId {

  /**
   * Builds a DTO from the given webhook entity.
   *
   * @param entity the source entity
   * @return the corresponding DTO
   */
  public static WebhookDto fromEntity(final WebhookEntity entity) {
    return new WebhookDto(
        entity.getId(),
        entity.getUrl(),
        entity.isActive(),
        entity.getLastStatus(),
        entity.getLastCalledAt());
  }
}
