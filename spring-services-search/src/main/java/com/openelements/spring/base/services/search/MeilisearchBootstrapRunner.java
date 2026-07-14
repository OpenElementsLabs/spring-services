package com.openelements.spring.base.services.search;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Full reindex on every backend startup. Discovers all {@link SearchIndexBootstrapStep} beans (in
 * {@code @Order} sequence), and for each one streams its documents into Meilisearch in batches.
 * Runs in the {@code searchIndexExecutor} pool (via {@link BootstrapInvoker}) so the HTTP listener
 * comes up immediately; while the bootstrap is in flight, {@link
 * SearchReadinessState#isBootstrapping()} stays {@code true}.
 *
 * <p>Each step runs inside its own {@code try/catch}: a failure in one step does not stop the
 * others, leaving a partial-but-usable index. A per-step success/failure summary is logged so
 * operators see partial-index state.
 */
@Component
@Order(30)
public class MeilisearchBootstrapRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MeilisearchBootstrapRunner.class);

  static final int BATCH_SIZE = 500;
  static final Duration TASK_WAIT = Duration.ofSeconds(10);

  private final List<SearchIndexBootstrapStep> steps;
  private final MeilisearchClient client;
  private final SearchReadinessState state;
  private final BootstrapInvoker invoker;

  public MeilisearchBootstrapRunner(
      final List<SearchIndexBootstrapStep> steps,
      final MeilisearchClient client,
      final SearchReadinessState state,
      final BootstrapInvoker invoker) {
    this.steps = steps;
    this.client = client;
    this.state = state;
    this.invoker = invoker;
  }

  @Override
  public void run(final ApplicationArguments args) {
    if (!client.isHealthy()) {
      log.warn("Skipping search-index bootstrap — Meilisearch is not reachable.");
      state.markBootstrappingFinished();
      return;
    }
    state.markBootstrappingStarted();
    invoker.run(this::executeAllSteps);
  }

  void executeAllSteps() {
    final long started = System.nanoTime();
    int succeeded = 0;
    int failed = 0;
    try {
      for (final SearchIndexBootstrapStep step : steps) {
        try (Stream<Map<String, Object>> docs = step.documents()) {
          final int pushed =
              BatchWriter.write(client, step.indexUid(), docs, BATCH_SIZE, TASK_WAIT);
          succeeded++;
          log.info("Bootstrap: pushed {} document(s) to {}.", pushed, step.indexUid());
        } catch (final RuntimeException e) {
          failed++;
          log.error(
              "Bootstrap step '{}' failed; remaining steps will still run.", step.indexUid(), e);
        }
      }
      log.info(
          "Bootstrap finished in {} ms: {} step(s) succeeded, {} failed.",
          (System.nanoTime() - started) / 1_000_000L,
          succeeded,
          failed);
    } finally {
      state.markBootstrappingFinished();
    }
  }
}
