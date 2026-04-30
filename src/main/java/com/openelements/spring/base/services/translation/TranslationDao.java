package com.openelements.spring.base.services.translation;

import com.openelements.spring.base.data.WithId;

import java.util.UUID;

public record TranslationDao(UUID id, String type, String idByType, Language language, String original,
                             String translation) implements WithId {
}
