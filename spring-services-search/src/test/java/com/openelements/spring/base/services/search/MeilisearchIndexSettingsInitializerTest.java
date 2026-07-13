package com.openelements.spring.base.services.search;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MeilisearchIndexSettingsInitializer}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two startup contracts: (1) for every {@link IndexSettings} bean discovered in the
 * application context, the initializer calls {@code ensureIndex(uid, primaryKey)} followed by
 * {@code updateSettings(uid, payload)} on the {@link MeilisearchClient}, and the payload carries
 * the three Meilisearch attribute keys the record exposes ({@code searchableAttributes},
 * {@code filterableAttributes}, {@code sortableAttributes}); (2) with no {@link IndexSettings}
 * beans, the initializer is a pure no-op — Meilisearch is never contacted.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with Mockito, no Spring context. The initializer is constructed manually with
 * a mocked client and an explicit bean list, then {@code run(null)} is invoked.
 *
 * <p><b>Mock-Audit.</b> One mock — {@link MeilisearchClient}. Justified: the unit under test is
 * a thin orchestrator whose job is precisely to call two client methods per bean in order, so
 * the test <em>is</em> the {@code verify(...)} call sequence. The wire-level behaviour of
 * {@code ensureIndex} and {@code updateSettings} is covered by {@link MeilisearchClientTest}
 * against WireMock. The payload shape is asserted via an {@code ArgumentCaptor} so the test
 * catches a regression where, say, {@code sortableAttributes} stops being forwarded.
 */
class MeilisearchIndexSettingsInitializerTest {

  /**
   * Confirms the per-bean call shape (ensureIndex + updateSettings) and asserts the
   * updateSettings payload contains the three keys the {@link IndexSettings} record exposes.
   * The captor is used so a future regression that silently drops one of the three attribute
   * sets surfaces immediately.
   */
  @Test
  @DisplayName(
      "Each IndexSettings bean is applied: ensureIndex(uid, primaryKey) then updateSettings with searchable/filterable/sortable attributes.")
  void appliesEachIndexSettingsBean() {
    final MeilisearchClient client = mock(MeilisearchClient.class);
    when(client.isHealthy()).thenReturn(true);
    final IndexSettings companies =
        new IndexSettings(
            "crm_companies", "id", List.of("name", "email"), List.of("brevo"), List.of());
    final IndexSettings tags =
        new IndexSettings("crm_tags", "id", List.of("name"), List.of(), List.of());

    new MeilisearchIndexSettingsInitializer(client, List.of(companies, tags)).run(null);

    verify(client).ensureIndex("crm_companies", "id");
    verify(client).ensureIndex("crm_tags", "id");

    final ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
    verify(client).updateSettings(eq("crm_companies"), captor.capture());
    final Map<String, Object> payload = captor.getValue();
    assertTrue(payload.containsKey("searchableAttributes"));
    assertTrue(payload.containsKey("filterableAttributes"));
    assertTrue(payload.containsKey("sortableAttributes"));
  }

  @Test
  @DisplayName(
      "An empty IndexSettings bean list is a no-op — neither ensureIndex nor updateSettings is invoked.")
  void emptyBeanListIsANoOp() {
    final MeilisearchClient client = mock(MeilisearchClient.class);

    new MeilisearchIndexSettingsInitializer(client, List.of()).run(null);

    verify(client, never()).ensureIndex(any(), any());
    verify(client, never()).updateSettings(any(), any());
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
    return ArgumentCaptor.forClass(Map.class);
  }
}
