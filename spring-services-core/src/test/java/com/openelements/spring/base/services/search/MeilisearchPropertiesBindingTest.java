package com.openelements.spring.base.services.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Binding tests for {@link MeilisearchProperties}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The {@code @ConfigurationProperties} prefix contract: the {@code openelements.meilisearch.*}
 * prefix populates the record fields, while the pre-rename {@code
 * openelements.search.meilisearch.*} prefix is silently ignored — leaving {@code host} unset
 * ({@code null}), since the record no longer defaults it. The legacy-prefix case acts as a
 * regression guard — if a future change reintroduces the longer prefix it must do so deliberately,
 * not by accident.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with Spring Boot's {@link Binder} driven against an in-memory {@link
 * MapConfigurationPropertySource}. No application context is started; binding is invoked
 * directly.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. Only Spring Boot's real binder is used over a real map-backed
 * property source.
 */
class MeilisearchPropertiesBindingTest {

  private static MeilisearchProperties bind(final Map<String, Object> properties) {
    final ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
    return new Binder(source).bindOrCreate("openelements.meilisearch", MeilisearchProperties.class);
  }

  @Test
  @DisplayName("The current openelements.meilisearch.host property binds onto MeilisearchProperties.host().")
  void renamedPrefixIsHonored() {
    final MeilisearchProperties props =
        bind(Map.of("openelements.meilisearch.host", "http://localhost:7700"));
    assertEquals("http://localhost:7700", props.host());
  }

  /**
   * Regression guard: the legacy {@code openelements.search.meilisearch.*} prefix must not bind.
   * Verified by asserting {@code host} stays {@code null} — the legacy-prefix value is ignored and
   * the record no longer supplies a default host.
   */
  @Test
  @DisplayName(
      "The legacy openelements.search.meilisearch.* prefix is ignored — host stays unset (null).")
  void legacyPrefixIsNoLongerHonored() {
    final MeilisearchProperties props =
        bind(Map.of("openelements.search.meilisearch.host", "http://example:7700"));
    assertNull(props.host());
  }
}
