package com.openelements.spring.base.services.translation;

import com.openelements.spring.base.data.EntityRepository;
import java.util.Optional;

public interface TranslationRepository extends EntityRepository<TranslationEntity> {

    Optional<TranslationEntity> findByTypeAndIdByTypeAndLanguage(String type, String idByType, Language language);
}
