package com.openelements.spring.base.search;

import com.openelements.spring.base.services.search.SearchConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional Meilisearch-backed search feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * feature self-activates when {@code spring-services-search} is on the classpath. The module carries
 * no extra third-party dependency (it talks to Meilisearch over Spring's {@code RestClient}), so
 * there is no heavy-dependency class to guard on; runtime opt-in is governed by
 * {@link SearchConfig}'s existing {@code @ConditionalOnProperty(openelements.meilisearch.enabled)}.
 *
 * <p>Lives outside the {@code services.search} package on purpose: {@link SearchConfig} declares a
 * {@code @ComponentScan} over its own package, and an auto-configuration co-located there would be
 * swept up by that scan and registered twice.
 */
@AutoConfiguration
@Import(SearchConfig.class)
public class SearchAutoConfiguration {}
