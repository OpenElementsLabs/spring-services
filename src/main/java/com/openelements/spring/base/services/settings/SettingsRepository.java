package com.openelements.spring.base.services.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link SettingsEntity} persistence operations. */
public interface SettingsRepository extends JpaRepository<SettingsEntity, String> {}
