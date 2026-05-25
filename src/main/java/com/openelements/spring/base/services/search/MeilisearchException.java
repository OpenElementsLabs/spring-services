package com.openelements.spring.base.services.search;

/** Thin runtime exception so callers can distinguish Meilisearch errors. */
public final class MeilisearchException extends RuntimeException {
  public MeilisearchException(final String message) {
    super(message);
  }
}
