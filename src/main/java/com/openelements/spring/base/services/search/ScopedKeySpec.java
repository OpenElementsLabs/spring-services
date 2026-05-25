package com.openelements.spring.base.services.search;

import java.util.List;

/**
 * Declarative description of the scoped Meilisearch API key the application wants minted at
 * startup. The application registers one such bean; the lib's {@link
 * MeilisearchScopedKeyInitializer} consumes it. When no bean is present, the client keeps using the
 * master key.
 *
 * @param indexes index patterns the key is restricted to (e.g. {@code my_index_*})
 * @param actions allowed Meilisearch actions (e.g. {@code search})
 */
public record ScopedKeySpec(List<String> indexes, List<String> actions) {}
