package com.openelements.spring.base.services.search;

import java.util.List;

/**
 * Declarative per-index Meilisearch settings. The application registers one bean per index; the
 * lib's {@link MeilisearchIndexSettingsInitializer} applies them idempotently at startup.
 *
 * @param indexUid fully resolved (prefixed) index UID
 * @param primaryKey document primary-key attribute
 * @param searchableAttributes attributes searched by Meilisearch
 * @param filterableAttributes attributes usable in filters (may be empty)
 * @param sortableAttributes attributes usable for sorting (may be empty)
 */
public record IndexSettings(
    String indexUid,
    String primaryKey,
    List<String> searchableAttributes,
    List<String> filterableAttributes,
    List<String> sortableAttributes) {}
