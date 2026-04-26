package com.openelements.spring.base.services.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties bound to the {@code audit.*} prefix.
 *
 * @param retentionDays number of days an audit entry is kept before {@link AuditCleanupJob} removes
 *     it; defaults to {@code 1460} (≈ 4 years)
 */
@ConfigurationProperties(prefix = "audit")
public record AuditProperties(@DefaultValue("1460") int retentionDays) {

  /** Default retention window in days (≈ 4 years). */
  public static final int DEFAULT_RETENTION_DAYS = 1460;

  /** Validates the configured value. */
  public AuditProperties {
    if (retentionDays < 0) {
      throw new IllegalArgumentException(
          "audit.retention-days must not be negative: " + retentionDays);
    }
  }
}
