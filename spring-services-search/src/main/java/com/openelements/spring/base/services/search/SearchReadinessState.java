package com.openelements.spring.base.services.search;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Shared mutable flag indicating whether {@link MeilisearchBootstrapRunner} has finished the
 * initial reindex. The bootstrap runs asynchronously and search endpoints short-circuit (e.g. to
 * 503) while this is {@code true}.
 */
@Component
public class SearchReadinessState {

  private final AtomicBoolean bootstrapping = new AtomicBoolean(true);

  /** Creates the readiness state, initially marked as bootstrapping. */
  public SearchReadinessState() {}

  /**
   * Returns whether the initial reindex is still running.
   *
   * @return {@code true} while the initial reindex is in progress, {@code false} once it finished
   */
  public boolean isBootstrapping() {
    return bootstrapping.get();
  }

  /** Marks the initial reindex as started (readiness flips to "not ready"). */
  public void markBootstrappingStarted() {
    bootstrapping.set(true);
  }

  /** Marks the initial reindex as finished (readiness flips to "ready"). */
  public void markBootstrappingFinished() {
    bootstrapping.set(false);
  }
}
