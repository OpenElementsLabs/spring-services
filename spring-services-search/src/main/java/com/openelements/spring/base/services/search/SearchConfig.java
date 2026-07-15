package com.openelements.spring.base.services.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Meilisearch-backed search feature.
 *
 * <p>Component-scans this package so that {@link MeilisearchClient}, the startup runners ({@link
 * MeilisearchScopedKeyInitializer}, {@link MeilisearchIndexSettingsInitializer}, {@link
 * MeilisearchBootstrapRunner}) and {@link SearchReadinessState} are picked up, and binds {@link
 * MeilisearchProperties}. Imported by {@link com.openelements.spring.base.FullSpringServiceConfig}
 * as part of the full platform setup.
 *
 * <p>This is a plain {@code @Configuration} (not {@code @AutoConfiguration}): the search runtime
 * beans are {@code @Component}s that must be discovered by component scanning, and {@link
 * MeilisearchClient} depends on the bound {@link MeilisearchProperties}. A plain
 * {@code @Configuration} is reliably picked up by a host application's {@code @ComponentScan}
 * (which excludes {@code @AutoConfiguration} classes), so the properties are always registered
 * alongside the components.
 *
 * <p>The feature is <strong>opt-in and disabled by default</strong>: none of these beans are
 * created unless {@code openelements.meilisearch.enabled=true}. This avoids forcing a Meilisearch
 * configuration on consumers that do not use search. When enabled, {@code
 * openelements.meilisearch.host} is required (see {@link MeilisearchProperties}) — a missing host
 * fails startup rather than defaulting to {@code localhost}.
 *
 * <p>Once enabled, the feature is still inert unless a Meilisearch sidecar is reachable: when it is
 * not, the startup runners log a warning and flip readiness to ready without indexing. The consuming
 * application supplies what the lib indexes — an optional {@link ScopedKeySpec} bean, one {@link
 * IndexSettings} bean per index, and one {@link SearchIndexBootstrapStep} bean per index — plus, for
 * asynchronous bootstrap, {@code @EnableAsync} and a {@code searchIndexExecutor} bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
@ComponentScan(basePackageClasses = SearchConfig.class)
@EnableConfigurationProperties(MeilisearchProperties.class)
public class SearchConfig {

  /** Creates the configuration. */
  public SearchConfig() {}
}
