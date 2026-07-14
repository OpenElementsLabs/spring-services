package com.openelements.spring.base.services.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for listing API keys. Does not contain the raw key.
 *
 * @param id the unique identifier of the API key
 * @param name the user-given name of the API key
 * @param keyPrefix the non-secret display prefix of the key
 * @param createdBy the name of the user who created the key
 * @param createdAt the timestamp at which the key was created
 */
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

  /**
   * Builds a DTO from the given API key entity, omitting the raw key value.
   *
   * @param entity the persisted API key entity
   * @return the corresponding DTO without the raw key
   */
  public static ApiKeyDto fromEntity(final ApiKeyEntity entity) {
    return new ApiKeyDto(
        entity.getId(),
        entity.getName(),
        entity.getKeyPrefix(),
        entity.getCreatedBy(),
        entity.getCreatedAt());
  }
}
