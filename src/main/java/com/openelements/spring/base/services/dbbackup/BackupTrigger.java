package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Who or what initiated a backup. */
public enum BackupTrigger {
  @JsonProperty("api")
  API,
  @JsonProperty("scheduler")
  SCHEDULER
}
