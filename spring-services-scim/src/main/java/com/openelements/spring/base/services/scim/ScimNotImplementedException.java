package com.openelements.spring.base.services.scim;

import org.springframework.http.HttpStatus;

/**
 * Thrown for SCIM operations this slice deliberately does not implement (group writes, user
 * {@code PATCH}). Maps to {@code 501 Not Implemented}.
 */
public class ScimNotImplementedException extends ScimException {

  /**
   * Creates the exception.
   *
   * @param detail the human-readable error detail
   */
  public ScimNotImplementedException(final String detail) {
    super(HttpStatus.NOT_IMPLEMENTED, null, detail);
  }
}
