package com.openelements.spring.base.services.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link SettingsEntity} persistence operations.
 *
 * <p>{@link SettingsEntity} inherits its primary key (a {@link UUID}) from {@code AbstractEntity}.
 * Application code looks settings up by their human-readable {@code key} column, so lookups and
 * deletions go through {@link #findByKey(String)} and {@link #deleteByKey(String)} rather than the
 * inherited primary-key methods.
 */
public interface SettingsRepository extends JpaRepository<SettingsEntity, UUID> {

  Optional<SettingsEntity> findByKey(String key);

  void deleteByKey(String key);
}
