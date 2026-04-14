package com.openelements.spring.base.data;

import java.util.UUID;

public record ForTestDto(UUID id, String name) implements WithId {}
