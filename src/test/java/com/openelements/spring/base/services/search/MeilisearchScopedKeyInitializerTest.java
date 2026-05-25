package com.openelements.spring.base.services.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MeilisearchScopedKeyInitializer}: a scoped key is minted only when a {@link
 * ScopedKeySpec} bean is present and Meilisearch is reachable; otherwise the client keeps the
 * master key.
 */
class MeilisearchScopedKeyInitializerTest {

  @Test
  void mintsScopedKeyWhenSpecPresentAndReachable() {
    final MeilisearchClient client = mock(MeilisearchClient.class);
    when(client.isHealthy()).thenReturn(true);
    when(client.createScopedKey(any(), any())).thenReturn("scoped-key");
    final ScopedKeySpec spec = new ScopedKeySpec(List.of("crm_*"), List.of("search"));

    new MeilisearchScopedKeyInitializer(client, Optional.of(spec)).run(null);

    verify(client).createScopedKey(List.of("crm_*"), List.of("search"));
    verify(client).useApiKey("scoped-key");
  }

  @Test
  void noSpecMeansNoKeyDerivation() {
    final MeilisearchClient client = mock(MeilisearchClient.class);

    new MeilisearchScopedKeyInitializer(client, Optional.empty()).run(null);

    verify(client, never()).createScopedKey(any(), any());
    verify(client, never()).useApiKey(any());
  }
}
