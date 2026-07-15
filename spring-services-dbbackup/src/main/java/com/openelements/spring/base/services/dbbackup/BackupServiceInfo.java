package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Service-level metadata returned by the sidecar's {@code GET /info} probe.
 *
 * @param version the sidecar's own software version
 * @param pgDumpVersion the version of the {@code pg_dump} tool the sidecar uses
 * @param retention how long backups are kept before being purged
 * @param backupInterval how often the sidecar's scheduler triggers a backup
 * @param backup runtime status of the most recent backup
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupServiceInfo(
    String version,
    String pgDumpVersion,
    Retention retention,
    BackupInterval backupInterval,
    Backup backup) {

  /**
   * Retention policy for stored backups.
   *
   * @param days the number of days backups are retained
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Retention(int days) {}

  /**
   * Schedule on which the sidecar automatically triggers backups.
   *
   * @param iso8601 the interval as an ISO-8601 duration string
   * @param seconds the same interval expressed in seconds
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BackupInterval(String iso8601, long seconds) {}

  /**
   * Runtime status of the most recent backup.
   *
   * @param lastSuccessfulBackupAgeSeconds age in seconds of the last successful backup
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Backup(long lastSuccessfulBackupAgeSeconds) {}
}
