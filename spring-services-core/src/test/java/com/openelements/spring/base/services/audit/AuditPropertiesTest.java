package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuditProperties}, the {@code @ConfigurationProperties} record that binds
 * {@code audit.retention-days} from {@code application.properties}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Three behaviours of the record: the constructor stores and exposes the configured retention
 * value, it fail-fasts on negative input (a misconfigured retention would silently lose audit data
 * forever), and the {@code DEFAULT_RETENTION_DAYS} public constant is pinned at 1460 days (~ four
 * years) — downstream applications depend on the default for compliance scenarios where
 * configuration is omitted.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests with AssertJ. No Spring context, no Testcontainers — the record is
 * exercised by direct construction.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. {@code AuditProperties} is a record with no collaborators.
 */
@DisplayName("AuditProperties")
class AuditPropertiesTest {

  @Test
  @DisplayName("AuditProperties.retentionDays() returns the value passed to the constructor.")
  void shouldExposeConfiguredRetention() {
    assertThat(new AuditProperties(30).retentionDays()).isEqualTo(30);
  }

  @Test
  @DisplayName(
      "Constructing AuditProperties with a negative retention throws IllegalArgumentException "
          + "naming the offending property — misconfiguration surfaces at startup, not at first cleanup.")
  void shouldRejectNegativeRetention() {
    assertThatThrownBy(() -> new AuditProperties(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("audit.retention-days");
  }

  @Test
  @DisplayName(
      "AuditProperties.DEFAULT_RETENTION_DAYS is pinned at 1460 (~ four years) — change requires "
          + "a deliberate compatibility review.")
  void shouldExposeDefaultConstant() {
    assertThat(AuditProperties.DEFAULT_RETENTION_DAYS).isEqualTo(1460);
  }
}
