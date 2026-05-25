package com.openelements.spring.base.services.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the Meilisearch sidecar. Bound from {@code openelements.meilisearch.*}
 * application properties.
 *
 * <p>This type is app-agnostic: it carries only the connection data and the index-prefix resolution
 * helper. By default no prefix is applied; an application that hosts several logical datasets in
 * one Meilisearch instance can set {@code openelements.meilisearch.index-prefix} to namespace its
 * indexes. The concrete index names live in the application, not here.
 */
@ConfigurationProperties("openelements.meilisearch")
public record MeilisearchProperties(
    String host, String masterKey, String indexPrefix, Duration requestTimeout) {

  public MeilisearchProperties {
    if (host == null || host.isBlank()) {
      host = "http://localhost:7700";
    }
    if (indexPrefix == null) {
      indexPrefix = "";
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofSeconds(5);
    }
  }

  /** Prepends the configured index prefix to {@code suffix}. */
  public String resolveIndex(final String suffix) {
    return indexPrefix + suffix;
  }
}
