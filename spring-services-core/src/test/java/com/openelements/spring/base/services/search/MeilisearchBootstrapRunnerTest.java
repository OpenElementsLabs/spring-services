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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MeilisearchBootstrapRunner}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The five invariants of the startup-time index population loop:
 *
 * <ol>
 *   <li><b>Batching.</b> Document streams are chunked to {@link
 *       MeilisearchBootstrapRunner#BATCH_SIZE} (500) per HTTP call, with a partial final batch
 *       flushed verbatim.
 *   <li><b>Stream lifecycle.</b> Each {@link SearchIndexBootstrapStep#documents()} {@link Stream}
 *       is closed exactly once — so a step backed by a JPA scroll cursor releases its DB resource.
 *   <li><b>Per-step error isolation.</b> A {@link RuntimeException} thrown inside one step does
 *       not prevent the runner from continuing with the remaining steps.
 *   <li><b>Readiness flip.</b> {@link SearchReadinessState#isBootstrapping()} flips to {@code
 *       false} on every path — successful, empty, partially-failed, fully-failed, and even when
 *       Meilisearch is unreachable at startup.
 *   <li><b>Unreachable-at-startup short-circuit.</b> When the client reports unhealthy, no step's
 *       {@code documents()} is called and readiness is still flipped, so the application becomes
 *       servable instead of stuck.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with Mockito, no Spring context, no network. A real {@link BootstrapInvoker}
 * is used because outside a Spring proxy it runs synchronously, which keeps the assertions
 * deterministic — wiring an executor would only add scheduling jitter without coverage.
 *
 * <p><b>Mock-Audit.</b> Two distinct mock surfaces.
 *
 * <ul>
 *   <li>{@link MeilisearchClient} — mocked across the suite. Justified: the runner's
 *       collaborator surface on the client ({@code addDocuments}, {@code waitForTask},
 *       {@code isHealthy}) is fully exercised by {@link MeilisearchClientTest} against WireMock.
 *       Driving the runner against a real client here would require WireMock <em>and</em> would
 *       not add any new coverage of the batching / lifecycle / readiness invariants.
 *   <li>{@link SearchIndexBootstrapStep} — mocked in <em>one</em> test
 *       ({@link #unreachableMeilisearchSkipsBootstrapAndMarksReady}) so we can verify
 *       {@code documents()} is never called. All other tests use real anonymous-class instances
 *       built by the {@code step(uid, supplier)} helper, which is the closest thing to "real
 *       collaborator" the abstraction allows.
 * </ul>
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

  /**
   * Exercises the batching invariant at exactly 3 × {@code BATCH_SIZE} documents — three HTTP
   * calls of 500 each, no partial batch. The captor confirms the per-call sizes rather than only
   * the call count, so a regression that misroutes documents into a smaller-than-batch chunk is
   * caught.
   */
  @Test
  @DisplayName(
      "A document stream of N × BATCH_SIZE is sent in exactly N calls, each carrying BATCH_SIZE documents.")
  void batchesAtFiveHundredDocuments() {
    buildRunner(
        List.of(step("crm_companies", () -> docs(MeilisearchBootstrapRunner.BATCH_SIZE * 3))));

    runner.executeAllSteps();

    final ArgumentCaptor<List<Map<String, Object>>> captor = listCaptor();
    verify(client, times(3)).addDocuments(eq("crm_companies"), captor.capture());
    assertEquals(List.of(500, 500, 500), captor.getAllValues().stream().map(List::size).toList());
  }

  @Test
  @DisplayName("A trailing partial batch (750 docs => 500 + 250) is flushed in a second call.")
  void flushesPartialFinalBatch() {
    buildRunner(List.of(step("crm_companies", () -> docs(750))));

    runner.executeAllSteps();

    final ArgumentCaptor<List<Map<String, Object>>> captor = listCaptor();
    verify(client, times(2)).addDocuments(eq("crm_companies"), captor.capture());
    assertEquals(List.of(500, 250), captor.getAllValues().stream().map(List::size).toList());
  }

  @Test
  @DisplayName(
      "An empty step issues zero addDocuments calls and still flips readiness to ready.")
  void emptyStepIsANoOp() {
    buildRunner(List.of(step("crm_companies", () -> docs(0))));

    runner.executeAllSteps();

    verify(client, never()).addDocuments(eq("crm_companies"), any());
    assertFalse(state.isBootstrapping(), "readiness must still flip");
  }

  /**
   * Guards the JPA-cursor resource hygiene: each step's {@code documents()} stream is closed
   * exactly once. {@code Stream.onClose(...)} fires when the runner's try-with-resources exits;
   * a regression that consumes the stream without closing it (or double-closes it) flips the
   * counter off 1.
   */
  @Test
  @DisplayName(
      "Each step's document stream is closed exactly once — guards the JPA-cursor resource lifecycle.")
  void closesTheDocumentStreamExactlyOnce() {
    final AtomicInteger closes = new AtomicInteger();
    buildRunner(List.of(step("crm_companies", () -> docs(10).onClose(closes::incrementAndGet))));

    runner.executeAllSteps();

    assertEquals(1, closes.get());
  }

  /**
   * Per-step error isolation: when the contacts step throws mid-stream, the runner must skip it
   * and still process tags and comments. Confirmed by capturing the uids the client saw — the
   * failing uid must be absent, the surviving uids must appear in the original order.
   */
  @Test
  @DisplayName(
      "A RuntimeException thrown by one step does not stop the others — surviving steps still run in order.")
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
  @DisplayName(
      "When every step throws, addDocuments is never called yet readiness still flips — the app stays servable.")
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

  /**
   * Verifies the early-out: when the client reports unhealthy at startup, the runner does not
   * even ask the step for documents (so a JPA scroll is not opened against an unreachable
   * search backend) and readiness is flipped so the app does not get stuck behind a never-
   * resolving search gate.
   */
  @Test
  @DisplayName(
      "If Meilisearch is unreachable at startup the runner never calls documents() and immediately marks the app ready.")
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
