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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MeilisearchIndexSettingsInitializer}: every registered {@link
 * IndexSettings} bean is applied (ensureIndex + updateSettings); an empty bean list is a no-op.
 */
class MeilisearchIndexSettingsInitializerTest {

  @Test
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
