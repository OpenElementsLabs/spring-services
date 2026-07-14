package com.openelements.spring.base.services.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes audit entries that are older than the configured retention window. */
@Component
public class AuditCleanupJob {

  private static final Logger LOG = LoggerFactory.getLogger(AuditCleanupJob.class);

  private final AuditLogRepository auditLogRepository;

  private final AuditProperties auditProperties;

  /**
   * Creates a new cleanup job.
   *
   * @param auditLogRepository the repository from which expired audit rows are deleted
   * @param auditProperties the configuration providing the retention window
   */
  public AuditCleanupJob(
      final AuditLogRepository auditLogRepository, final AuditProperties auditProperties) {
    this.auditLogRepository =
        Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
    this.auditProperties =
        Objects.requireNonNull(auditProperties, "auditProperties must not be null");
  }

  /**
   * Deletes every audit row whose {@code createdAt} is strictly older than {@code now -
   * retentionDays}.
   *
   * <p>Runs every day at 02:00 server time by default; the schedule can be overridden by replacing
   * the bean.
   */
  @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
  @Transactional
  public void cleanupExpiredEntries() {
    final Instant cutoff = Instant.now().minus(auditProperties.retentionDays(), ChronoUnit.DAYS);
    final int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
    LOG.info("Removed {} audit entries older than {}", deleted, cutoff);
  }
}
