package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lifecycle states a backup job moves through. Mirrors the lowercase wire values the sidecar emits
 * in its JSON payloads.
 */
public enum BackupJobStatus {
  /** The job has been accepted and is waiting to start. */
  @JsonProperty("queued")
  QUEUED,
  /** The job is currently producing a backup. */
  @JsonProperty("running")
  RUNNING,
  /** The job completed and a backup artefact was produced. */
  @JsonProperty("succeeded")
  SUCCEEDED,
  /** The job ended with an error and no usable backup was produced. */
  @JsonProperty("failed")
  FAILED
}
