package com.openelements.spring.base.services.scim;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a SCIM request payload is invalid (e.g. missing the required {@code userName}). Maps
 * to {@code 400 Bad Request} with {@code scimType: "invalidValue"} (RFC 7644 §3.3).
 */
public class ScimValidationException extends ScimException {

  /**
   * Creates the exception.
   *
   * @param detail the human-readable error detail
   */
  public ScimValidationException(final String detail) {
    super(HttpStatus.BAD_REQUEST, "invalidValue", detail);
  }
}
