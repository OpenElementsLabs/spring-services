package com.openelements.spring.services.apikey;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time response DTO for a newly created API key. Contains the raw key
 * which is shown exactly once and never stored or retrievable again.
 */
@Schema(description = "Newly created API key (raw key shown only once)")
public record ApiKeyCreatedDto(
        @Schema(description = "API key ID", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(description = "User-given name", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "Display prefix", requiredMode = Schema.RequiredMode.REQUIRED) String keyPrefix,
        @Schema(description = "Raw API key — copy now, it will not be shown again", requiredMode = Schema.RequiredMode.REQUIRED) String key,
        @Schema(description = "Name of the user who created this key", requiredMode = Schema.RequiredMode.REQUIRED) String createdBy,
        @Schema(description = "Creation timestamp", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
}
