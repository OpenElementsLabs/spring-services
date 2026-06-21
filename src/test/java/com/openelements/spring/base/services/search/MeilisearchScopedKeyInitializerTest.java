package com.openelements.spring.base.services.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MeilisearchScopedKeyInitializer}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The startup contract that downgrades the {@link MeilisearchClient} from the master key to a
 * scoped key. Two paths are covered: when a {@link ScopedKeySpec} bean is present and
 * Meilisearch is reachable, the initializer mints a scoped key via {@code createScopedKey(...)}
 * and installs it via {@code useApiKey(...)}; when no spec bean is present (typical for tests
 * and lean profiles), the initializer is a no-op and the client keeps whatever key it was
 * constructed with — usually the master key.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with Mockito, no Spring context. {@link MeilisearchClient} is a Mockito mock;
 * the initializer is constructed manually and {@code run(null)} is invoked directly.
 *
 * <p><b>Mock-Audit.</b> One mock — {@link MeilisearchClient}. Justified: the initializer's job is
 * exactly to call two methods on the client in the right order; the test is precisely the
 * Mockito {@code verify(...)} assertion of those two calls. A real client would force a WireMock
 * sidecar (covered separately by {@link MeilisearchClientTest}) and add no behavioural coverage
 * to this initializer's logic. The reachability gate ({@code isHealthy()}) is similarly stubbed
 * to keep the test deterministic.
 */
class MeilisearchScopedKeyInitializerTest {

  @Test
  @DisplayName(
      "Given a ScopedKeySpec and a reachable Meilisearch, the initializer mints a scoped key and installs it on the client.")
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
  @DisplayName(
      "Without a ScopedKeySpec bean the initializer is a no-op — neither createScopedKey nor useApiKey is called.")
  void noSpecMeansNoKeyDerivation() {
    final MeilisearchClient client = mock(MeilisearchClient.class);

    new MeilisearchScopedKeyInitializer(client, Optional.empty()).run(null);

    verify(client, never()).createScopedKey(any(), any());
    verify(client, never()).useApiKey(any());
  }
}
