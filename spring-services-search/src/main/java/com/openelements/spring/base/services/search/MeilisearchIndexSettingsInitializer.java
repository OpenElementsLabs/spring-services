package com.openelements.spring.base.services.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Idempotently writes the per-index Meilisearch settings (searchable / filterable / sortable
 * attributes) for every registered {@link IndexSettings} bean. Runs once at startup, after {@link
 * MeilisearchScopedKeyInitializer}. Re-running the same settings is a no-op for Meilisearch. When
 * no {@code IndexSettings} beans are registered, this is a no-op.
 */
@Component
@Order(20)
public class MeilisearchIndexSettingsInitializer implements ApplicationRunner {

  private static final Logger log =
      LoggerFactory.getLogger(MeilisearchIndexSettingsInitializer.class);

  private final MeilisearchClient client;
  private final List<IndexSettings> settings;

  /**
   * Creates the index-settings initializer.
   *
   * @param client the Meilisearch client used to ensure indexes and write settings
   * @param settings all registered per-index settings beans
   */
  public MeilisearchIndexSettingsInitializer(
      final MeilisearchClient client, final List<IndexSettings> settings) {
    this.client = client;
    this.settings = settings;
  }

  @Override
  public void run(final ApplicationArguments args) {
    if (settings.isEmpty()) {
      log.debug("No IndexSettings beans present — skipping index-settings configuration.");
      return;
    }
    if (!client.isHealthy()) {
      log.warn("Skipping index-settings configuration — Meilisearch is not reachable.");
      return;
    }
    try {
      for (final IndexSettings s : settings) {
        client.ensureIndex(s.indexUid(), s.primaryKey());
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("searchableAttributes", s.searchableAttributes());
        payload.put("filterableAttributes", s.filterableAttributes());
        payload.put("sortableAttributes", s.sortableAttributes());
        client.updateSettings(s.indexUid(), payload);
      }
      log.info("Meilisearch: index settings configured for {} index(es).", settings.size());
    } catch (final RuntimeException e) {
      log.error(
          "Meilisearch: failed to configure index settings — search may behave "
              + "unexpectedly. Error: {}",
          e.toString(),
          e);
    }
  }
}
