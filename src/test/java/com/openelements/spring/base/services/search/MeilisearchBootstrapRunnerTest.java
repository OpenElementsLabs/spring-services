package com.openelements.spring.base.services.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MeilisearchBootstrapRunner}: batching, stream lifecycle, per-step error
 * isolation, readiness flipping, and the unreachable-at-startup path. All deterministic — no
 * Spring, no network.
 */
class MeilisearchBootstrapRunnerTest {

  private MeilisearchClient client;
  private SearchReadinessState state;
  private MeilisearchBootstrapRunner runner;

  @BeforeEach
  void setUp() {
    client = mock(MeilisearchClient.class);
    when(client.addDocuments(any(), any())).thenReturn(1L);
    when(client.waitForTask(anyLong(), any())).thenReturn(TaskOutcome.SUCCEEDED);
    state = new SearchReadinessState();
  }

  private void buildRunner(final List<SearchIndexBootstrapStep> steps) {
    // A real BootstrapInvoker runs synchronously here (no @Async proxy
    // outside Spring), which keeps the assertions deterministic.
    runner = new MeilisearchBootstrapRunner(steps, client, state, new BootstrapInvoker());
  }

  private static SearchIndexBootstrapStep step(
      final String uid, final java.util.function.Supplier<Stream<Map<String, Object>>> docs) {
    return new SearchIndexBootstrapStep() {
      @Override
      public String indexUid() {
        return uid;
      }

      @Override
      public Stream<Map<String, Object>> documents() {
        return docs.get();
      }
    };
  }

  private static Stream<Map<String, Object>> docs(final int count) {
    return IntStream.range(0, count)
        .<Map<String, Object>>mapToObj(
            i -> {
              final Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", "doc-" + i);
              return m;
            });
  }

  @Test
  void batchesAtFiveHundredDocuments() {
    buildRunner(
        List.of(step("crm_companies", () -> docs(MeilisearchBootstrapRunner.BATCH_SIZE * 3))));

    runner.executeAllSteps();

    final ArgumentCaptor<List<Map<String, Object>>> captor = listCaptor();
    verify(client, times(3)).addDocuments(eq("crm_companies"), captor.capture());
    assertEquals(List.of(500, 500, 500), captor.getAllValues().stream().map(List::size).toList());
  }

  @Test
  void flushesPartialFinalBatch() {
    buildRunner(List.of(step("crm_companies", () -> docs(750))));

    runner.executeAllSteps();

    final ArgumentCaptor<List<Map<String, Object>>> captor = listCaptor();
    verify(client, times(2)).addDocuments(eq("crm_companies"), captor.capture());
    assertEquals(List.of(500, 250), captor.getAllValues().stream().map(List::size).toList());
  }

  @Test
  void emptyStepIsANoOp() {
    buildRunner(List.of(step("crm_companies", () -> docs(0))));

    runner.executeAllSteps();

    verify(client, never()).addDocuments(eq("crm_companies"), any());
    assertFalse(state.isBootstrapping(), "readiness must still flip");
  }

  @Test
  void closesTheDocumentStreamExactlyOnce() {
    final AtomicInteger closes = new AtomicInteger();
    buildRunner(List.of(step("crm_companies", () -> docs(10).onClose(closes::incrementAndGet))));

    runner.executeAllSteps();

    assertEquals(1, closes.get());
  }

  @Test
  void failureInOneStepDoesNotStopTheOthers() {
    final SearchIndexBootstrapStep companies = step("crm_companies", () -> docs(1));
    final SearchIndexBootstrapStep contacts =
        step(
            "crm_contacts",
            () ->
                docs(1)
                    .map(
                        d -> {
                          throw new RuntimeException("boom");
                        }));
    final SearchIndexBootstrapStep tags = step("crm_tags", () -> docs(1));
    final SearchIndexBootstrapStep comments = step("crm_comments", () -> docs(1));
    buildRunner(List.of(companies, contacts, tags, comments));

    runner.executeAllSteps();

    final ArgumentCaptor<String> uids = ArgumentCaptor.forClass(String.class);
    verify(client, times(3)).addDocuments(uids.capture(), any());
    assertEquals(List.of("crm_companies", "crm_tags", "crm_comments"), uids.getAllValues());
    assertFalse(state.isBootstrapping(), "readiness must flip even with a failed step");
  }

  @Test
  void readinessFlipsEvenWhenAllStepsFail() {
    final List<SearchIndexBootstrapStep> steps = new ArrayList<>();
    for (final String uid : List.of("crm_companies", "crm_contacts", "crm_tags", "crm_comments")) {
      steps.add(
          step(
              uid,
              () -> {
                throw new RuntimeException("boom " + uid);
              }));
    }
    buildRunner(steps);

    runner.executeAllSteps();

    verify(client, never()).addDocuments(any(), any());
    assertFalse(state.isBootstrapping());
  }

  @Test
  void unreachableMeilisearchSkipsBootstrapAndMarksReady() {
    when(client.isHealthy()).thenReturn(false);
    final SearchIndexBootstrapStep companies = mock(SearchIndexBootstrapStep.class);
    buildRunner(List.of(companies));

    runner.run(null);

    verify(companies, never()).documents();
    verify(client, never()).addDocuments(any(), any());
    assertFalse(state.isBootstrapping());
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<Map<String, Object>>> listCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }
}
