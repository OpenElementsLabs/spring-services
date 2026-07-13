package com.openelements.spring.base.services.search;

import java.util.Map;
import java.util.stream.Stream;

/**
 * SPI for one entity type's contribution to the startup reindex. The lib drives all registered
 * steps; the application supplies one implementation per index.
 *
 * <p>The lib never sees domain types — steps deliver already-mapped {@code Map<String, Object>}
 * documents. Step execution order is the Spring bean {@code @Order}, not a method on this
 * interface.
 */
public interface SearchIndexBootstrapStep {

  /** Target index UID (already prefixed). Used for the Meilisearch write and for log identity. */
  String indexUid();

  /**
   * Lazy stream of documents to push. The lib batches into groups of {@link
   * MeilisearchBootstrapRunner#BATCH_SIZE} and flushes via {@link MeilisearchClient#addDocuments}.
   * The lib closes the stream via try-with-resources.
   */
  Stream<Map<String, Object>> documents();
}
