package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Who or what initiated a backup. */
public enum BackupTrigger {
  /** The backup was initiated by an explicit API call. */
  @JsonProperty("api")
  API,
  /** The backup was initiated automatically by the sidecar's scheduler. */
  @JsonProperty("scheduler")
  SCHEDULER
}
