package com.openelements.spring.base.services.search;

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
 * <p>The feature is inert unless a Meilisearch sidecar is reachable: when it is not, the startup
 * runners log a warning and flip readiness to ready without indexing. The consuming application
 * supplies what the lib indexes — an optional {@link ScopedKeySpec} bean, one {@link IndexSettings}
 * bean per index, and one {@link SearchIndexBootstrapStep} bean per index — plus, for asynchronous
 * bootstrap, {@code @EnableAsync} and a {@code searchIndexExecutor} bean.
 */
@Configuration
@ComponentScan
@EnableConfigurationProperties(MeilisearchProperties.class)
public class SearchConfig {}
