package com.openelements.spring.base.services.scim;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a SCIM resource is not found, or has been soft-deleted and is therefore absent from
 * the SCIM view. Maps to {@code 404 Not Found}.
 */
public class ScimNotFoundException extends ScimException {

  /**
   * Creates the exception.
   *
   * @param detail the human-readable error detail
   */
  public ScimNotFoundException(final String detail) {
    super(HttpStatus.NOT_FOUND, null, detail);
  }
}
