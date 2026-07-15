package com.openelements.spring.base.services.system;

import com.openelements.spring.base.data.WithId;
import java.util.UUID;

/**
 * DTO describing an external system integration.
 *
 * @param id the unique identifier of the system
 * @param name the display name of the system
 * @param description a human-readable description of the system
 * @param apiKey the API key used to authenticate against the system
 * @param baseUrl the base URL of the system
 */
public record SystemDto(UUID id, String name, String description, String apiKey, String baseUrl)
    implements WithId {}
