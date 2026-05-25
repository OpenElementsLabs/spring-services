/**
 * Reusable Meilisearch-backed search integration.
 *
 * <p>This package provides the building blocks for indexing application data into a <a
 * href="https://www.meilisearch.com/">Meilisearch</a> sidecar and querying it. The lib is
 * deliberately app-agnostic: it never sees domain types and takes no stance on which indexes exist.
 * The consuming application supplies the documents and the index definitions; the lib drives the
 * Meilisearch REST API.
 *
 * <h2>Core types</h2>
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.services.search.MeilisearchClient} — thin HTTP wrapper
 *       around the Meilisearch REST API, built on Spring's {@code RestClient}.
 *   <li>{@link com.openelements.spring.base.services.search.SearchIndexBootstrapStep} — SPI the
 *       application implements (one bean per index) to stream documents into the startup reindex.
 *   <li>{@link com.openelements.spring.base.services.search.IndexSettings} and {@link
 *       com.openelements.spring.base.services.search.ScopedKeySpec} — declarative beans describing
 *       per-index settings and the scoped runtime key to mint at startup.
 *   <li>{@link com.openelements.spring.base.services.search.Highlighter} — turns Meilisearch's
 *       {@code _formatted} output into HTML-safe highlighted fragments.
 *   <li>{@link com.openelements.spring.base.services.search.SearchReadinessState} — shared flag
 *       that stays "not ready" while the initial reindex is in flight.
 * </ul>
 *
 * <h2>Startup sequence</h2>
 *
 * <p>Three {@code ApplicationRunner}s run in {@code @Order}: {@link
 * com.openelements.spring.base.services.search.MeilisearchScopedKeyInitializer} (10) exchanges the
 * master key for a scoped runtime key, {@link
 * com.openelements.spring.base.services.search.MeilisearchIndexSettingsInitializer} (20) applies
 * per-index settings, and {@link
 * com.openelements.spring.base.services.search.MeilisearchBootstrapRunner} (30) performs the full
 * reindex. Each step is resilient: a failure leaves a partial-but-usable index, and an unreachable
 * sidecar is skipped with a warning.
 *
 * <h2>Configuration</h2>
 *
 * <p>Connection settings bind from the {@code openelements.meilisearch} prefix via {@link
 * com.openelements.spring.base.services.search.MeilisearchProperties}. The master key must be
 * supplied through environment variables or secret management — never committed to source control.
 */
package com.openelements.spring.base.services.search;
