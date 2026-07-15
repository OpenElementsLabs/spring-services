package com.openelements.spring.base.services.search;

/** Terminal state of a Meilisearch async task as observed by polling. */
public enum TaskOutcome {

  /** The task completed successfully. */
  SUCCEEDED,

  /** The task ended in a failed or canceled state, or polling was interrupted. */
  FAILED,

  /** The task did not reach a terminal state before the polling timeout elapsed. */
  TIMED_OUT
}
