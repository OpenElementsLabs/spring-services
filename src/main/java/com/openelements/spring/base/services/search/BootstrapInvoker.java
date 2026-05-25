package com.openelements.spring.base.services.search;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Indirection so {@link MeilisearchBootstrapRunner} can run the reindex on the {@code
 * searchIndexExecutor} pool via Spring's {@code @Async} proxy (a self-invoked {@code @Async} method
 * would not be proxied). Tests can replace this bean with a synchronous implementation for
 * deterministic assertions.
 *
 * <p>The {@code searchIndexExecutor} and {@code @EnableAsync} are supplied by the consuming
 * application. Without them the reindex simply runs synchronously at startup.
 */
@Component
public class BootstrapInvoker {

  @Async("searchIndexExecutor")
  public void run(final Runnable task) {
    task.run();
  }
}
