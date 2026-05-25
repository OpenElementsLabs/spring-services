package com.openelements.spring.base.services.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams documents into Meilisearch in fixed-size batches. For each batch it issues a single
 * {@link MeilisearchClient#addDocuments} call and waits for the resulting task to settle. A
 * non-success outcome is logged but does not abort the remaining batches — the caller decides how
 * to treat a failed step.
 */
final class BatchWriter {

  private static final Logger log = LoggerFactory.getLogger(BatchWriter.class);

  private BatchWriter() {}

  /**
   * Consumes {@code docs} in groups of {@code batchSize}, pushing each group to {@code indexUid}.
   * The caller owns the stream's lifecycle (this method does not close it).
   *
   * @return the total number of documents pushed
   */
  static int write(
      final MeilisearchClient client,
      final String indexUid,
      final Stream<Map<String, Object>> docs,
      final int batchSize,
      final Duration taskWait) {
    final List<Map<String, Object>> batch = new ArrayList<>(batchSize);
    int pushed = 0;
    final Iterator<Map<String, Object>> it = docs.iterator();
    while (it.hasNext()) {
      batch.add(it.next());
      if (batch.size() >= batchSize) {
        pushed += flush(client, indexUid, batch, taskWait);
      }
    }
    if (!batch.isEmpty()) {
      pushed += flush(client, indexUid, batch, taskWait);
    }
    return pushed;
  }

  private static int flush(
      final MeilisearchClient client,
      final String indexUid,
      final List<Map<String, Object>> batch,
      final Duration taskWait) {
    final int n = batch.size();
    final long taskUid = client.addDocuments(indexUid, List.copyOf(batch));
    batch.clear();
    final TaskOutcome outcome = client.waitForTask(taskUid, taskWait);
    if (outcome != TaskOutcome.SUCCEEDED) {
      log.warn(
          "Meilisearch addDocuments task {} for {} ended with status {} — "
              + "batch of {} documents may not be searchable until next restart.",
          taskUid,
          indexUid,
          outcome,
          n);
    }
    return n;
  }
}
