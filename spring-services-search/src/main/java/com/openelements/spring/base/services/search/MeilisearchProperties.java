package com.openelements.spring.base.services.search;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the Meilisearch sidecar. Bound from {@code openelements.meilisearch.*}
 * application properties.
 *
 * <p>The search feature is <strong>opt-in</strong>: it is disabled by default and only activates
 * when {@code openelements.meilisearch.enabled=true} (see {@link SearchConfig}). When the feature is
 * enabled, {@code host} is <strong>required</strong> — there is no default. A blank or missing host
 * fails context startup with a binding/validation error rather than silently pointing at
 * {@code localhost}, so a misconfiguration surfaces immediately instead of at first use.
 *
 * <p>This type is app-agnostic: it carries only the connection data and the index-prefix resolution
 * helper. By default no prefix is applied; an application that hosts several logical datasets in
 * one Meilisearch instance can set {@code openelements.meilisearch.index-prefix} to namespace its
 * indexes. The concrete index names live in the application, not here.
 *
 * @param host base URL of the Meilisearch instance; required and must not be blank
 * @param masterKey master API key used at startup before it is exchanged for a scoped key; may be
 *     {@code null} if the instance runs without authentication
 * @param indexPrefix prefix prepended to every index name to namespace this application's indexes;
 *     defaults to an empty string (no prefix) when not configured
 * @param requestTimeout timeout applied to HTTP calls against Meilisearch; defaults to 5 seconds
 *     when not configured
 */
@Validated
@ConfigurationProperties("openelements.meilisearch")
public record MeilisearchProperties(
    @NotBlank String host, String masterKey, String indexPrefix, Duration requestTimeout) {

  /** Applies defaults for optional components: empty index prefix and a 5-second request timeout. */
  public MeilisearchProperties {
    if (indexPrefix == null) {
      indexPrefix = "";
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofSeconds(5);
    }
  }

  /**
   * Prepends the configured index prefix to {@code suffix}.
   *
   * @param suffix the base index name to namespace
   * @return the fully resolved index name, i.e. the configured prefix followed by {@code suffix}
   */
  public String resolveIndex(final String suffix) {
    return indexPrefix + suffix;
  }
}
