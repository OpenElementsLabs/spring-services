package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lifecycle states a backup job moves through. Mirrors the lowercase wire values the sidecar emits
 * in its JSON payloads.
 */
public enum BackupJobStatus {
  @JsonProperty("queued")
  QUEUED,
  @JsonProperty("running")
  RUNNING,
  @JsonProperty("succeeded")
  SUCCEEDED,
  @JsonProperty("failed")
  FAILED
}
