package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuditProperties")
class AuditPropertiesTest {

  @Test
  void shouldExposeConfiguredRetention() {
    assertThat(new AuditProperties(30).retentionDays()).isEqualTo(30);
  }

  @Test
  void shouldRejectNegativeRetention() {
    assertThatThrownBy(() -> new AuditProperties(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("audit.retention-days");
  }

  @Test
  void shouldExposeDefaultConstant() {
    assertThat(AuditProperties.DEFAULT_RETENTION_DAYS).isEqualTo(1460);
  }
}
