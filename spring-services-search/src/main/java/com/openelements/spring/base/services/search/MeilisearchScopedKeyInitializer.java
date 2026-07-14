package com.openelements.spring.base.services.search;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Exchanges the master Meilisearch API key for a scoped runtime key when a {@link ScopedKeySpec}
 * bean is present. Runs once at application startup, before {@link
 * MeilisearchIndexSettingsInitializer} and {@link MeilisearchBootstrapRunner}.
 *
 * <p>If no {@code ScopedKeySpec} bean is registered, no key is derived and the client keeps using
 * the master key. If the exchange fails, the client also keeps the master key — degraded but
 * functional. A WARN is logged so operators see the regression.
 */
@Component
@Order(10)
public class MeilisearchScopedKeyInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MeilisearchScopedKeyInitializer.class);

  private final MeilisearchClient client;
  private final Optional<ScopedKeySpec> scopedKey;

  public MeilisearchScopedKeyInitializer(
      final MeilisearchClient client, final Optional<ScopedKeySpec> scopedKey) {
    this.client = client;
    this.scopedKey = scopedKey;
  }

  @Override
  public void run(final ApplicationArguments args) {
    if (scopedKey.isEmpty()) {
      log.debug("No ScopedKeySpec bean present — keeping the master key.");
      return;
    }
    if (!client.isHealthy()) {
      log.warn(
          "Meilisearch is not reachable at startup. Search will be unavailable "
              + "until the sidecar is healthy.");
      return;
    }
    final ScopedKeySpec spec = scopedKey.get();
    try {
      final String scoped = client.createScopedKey(spec.indexes(), spec.actions());
      client.useApiKey(scoped);
      log.info(
          "Meilisearch: exchanged master key for scoped runtime key (indexes: {}).",
          spec.indexes());
    } catch (final RuntimeException e) {
      log.warn(
          "Meilisearch: failed to mint a scoped key — runtime calls will continue "
              + "with the master key. Error: {}",
          e.toString());
    }
  }
}
