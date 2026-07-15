package com.openelements.spring.base.services.search;

/** Thin runtime exception so callers can distinguish Meilisearch errors. */
public final class MeilisearchException extends RuntimeException {
  /**
   * Creates the exception with the given detail message.
   *
   * @param message the detail message describing the Meilisearch error
   */
  public MeilisearchException(final String message) {
    super(message);
  }
}
