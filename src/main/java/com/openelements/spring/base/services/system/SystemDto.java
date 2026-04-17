package com.openelements.spring.base.services.system;

import com.openelements.spring.base.data.WithId;

import java.util.UUID;

public record SystemDto(UUID id, String name, String description, String apiKey, String baseUrl) implements WithId {
}
