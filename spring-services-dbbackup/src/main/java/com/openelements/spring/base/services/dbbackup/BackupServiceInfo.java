package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Service-level metadata returned by the sidecar's {@code GET /info} probe. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupServiceInfo(
    String version,
    String pgDumpVersion,
    Retention retention,
    BackupInterval backupInterval,
    Backup backup) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Retention(int days) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BackupInterval(String iso8601, long seconds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Backup(long lastSuccessfulBackupAgeSeconds) {}
}
