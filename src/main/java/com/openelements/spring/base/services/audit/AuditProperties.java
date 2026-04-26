package com.openelements.spring.base.services.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds the audit-log retention configuration.
 *
 * <p>Bound via {@link Value} on a single {@code @Component} so the bean is always discoverable by
 * the standard Spring component scan — no separate {@code @EnableConfigurationProperties}
 * registration is required.
 *
 * @param retentionDays number of days an audit entry is kept before {@link AuditCleanupJob} removes
 *     it; configurable via {@code audit.retention-days} and defaults to {@value
 *     #DEFAULT_RETENTION_DAYS} (≈ 4 years)
 */
@Component
public record AuditProperties(int retentionDays) {

  /** Default retention window in days (≈ 4 years). */
  public static final int DEFAULT_RETENTION_DAYS = 1460;

  /**
   * @param retentionDays bound from the {@code audit.retention-days} property
   */
  public AuditProperties(@Value("${audit.retention-days:1460}") final int retentionDays) {
    this.retentionDays = retentionDays;
    if (retentionDays < 0) {
      throw new IllegalArgumentException(
          "audit.retention-days must not be negative: " + retentionDays);
    }
  }
}
