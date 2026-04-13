package com.openelements.spring.base.services.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Service for reading and writing application settings.
 */
@Service
@Transactional
public class SettingsDataService {

    private final SettingsRepository settingsRepository;

    public SettingsDataService(final SettingsRepository settingsRepository) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository must not be null");
    }

    /**
     * Returns the value for a setting key.
     *
     * @param key the setting key
     * @return the value, or empty if not set
     */
    @Transactional(readOnly = true)
    public Optional<String> get(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        return settingsRepository.findById(key).map(SettingsEntity::getValue);
    }

    /**
     * Sets a setting value (insert or update).
     *
     * @param key   the setting key
     * @param value the setting value
     */
    public void set(final String key, final String value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        final SettingsEntity entity = settingsRepository.findById(key)
                .orElseGet(() -> {
                    final SettingsEntity e = new SettingsEntity();
                    e.setKey(key);
                    return e;
                });
        entity.setValue(value);
        settingsRepository.saveAndFlush(entity);
    }

    /**
     * Removes a setting.
     *
     * @param key the setting key
     */
    public void delete(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        settingsRepository.deleteById(key);
    }
}
