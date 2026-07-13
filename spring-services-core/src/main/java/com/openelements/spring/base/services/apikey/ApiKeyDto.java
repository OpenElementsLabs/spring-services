package com.openelements.spring.base.services.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** DTO for listing API keys. Does not contain the raw key. */
@Schema(description = "API Key")
public record ApiKeyDto(
    @Schema(description = "API key ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "User-given name", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "Display prefix (e.g. crm_a1B2...w3X4)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String keyPrefix,
    @Schema(
            description = "Name of the user who created this key",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String createdBy,
    @Schema(description = "Creation timestamp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {

  public static ApiKeyDto fromEntity(final ApiKeyEntity entity) {
    return new ApiKeyDto(
        entity.getId(),
        entity.getName(),
        entity.getKeyPrefix(),
        entity.getCreatedBy(),
        entity.getCreatedAt());
  }
}
