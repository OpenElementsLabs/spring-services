package com.openelements.spring.base.services.search;

/** Terminal state of a Meilisearch async task as observed by polling. */
public enum TaskOutcome {
  SUCCEEDED,
  FAILED,
  TIMED_OUT
}
