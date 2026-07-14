package com.openelements.spring.base.services.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One-time response DTO for a newly created API key. Contains the raw key which is shown exactly
 * once and never stored or retrievable again.
 *
 * @param id the unique identifier of the API key
 * @param name the user-given name of the API key
 * @param keyPrefix the non-secret display prefix of the key
 * @param key the raw API key value, shown only once and never retrievable again
 * @param createdBy the name of the user who created the key
 * @param createdAt the timestamp at which the key was created
 */
@Schema(description = "Newly created API key (raw key shown only once)")
public record ApiKeyCreatedDto(
    @Schema(description = "API key ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "User-given name", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(description = "Display prefix", requiredMode = Schema.RequiredMode.REQUIRED)
        String keyPrefix,
    @Schema(
            description = "Raw API key — copy now, it will not be shown again",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String key,
    @Schema(
            description = "Name of the user who created this key",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String createdBy,
    @Schema(description = "Creation timestamp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {}
