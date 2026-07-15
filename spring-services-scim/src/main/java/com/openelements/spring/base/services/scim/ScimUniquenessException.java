package com.openelements.spring.base.services.scim;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a SCIM {@code POST} would violate the uniqueness of {@code externalId} or
 * {@code userName}. Maps to {@code 409 Conflict} with {@code scimType: "uniqueness"} (RFC 7644
 * §3.3).
 */
public class ScimUniquenessException extends ScimException {

  /**
   * Creates the exception.
   *
   * @param detail the human-readable error detail
   */
  public ScimUniquenessException(final String detail) {
    super(HttpStatus.CONFLICT, "uniqueness", detail);
  }
}
