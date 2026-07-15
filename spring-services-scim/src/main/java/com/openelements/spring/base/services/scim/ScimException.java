package com.openelements.spring.base.services.scim;

import org.springframework.http.HttpStatus;

/**
 * Base type for SCIM error conditions that the {@code ScimExceptionHandler} maps to an RFC 7644
 * {@code Error} envelope. Carries the HTTP status and the optional SCIM {@code scimType} keyword.
 */
public abstract class ScimException extends RuntimeException {

  /** The HTTP status to return for this error. */
  private final HttpStatus status;

  /** The SCIM {@code scimType} keyword for this error, or {@code null} if none applies. */
  private final String scimType;

  /**
   * Creates a SCIM exception.
   *
   * @param status the HTTP status to return
   * @param scimType the SCIM error keyword (e.g. {@code uniqueness}), or {@code null}
   * @param detail the human-readable error detail
   */
  protected ScimException(final HttpStatus status, final String scimType, final String detail) {
    super(detail);
    this.status = status;
    this.scimType = scimType;
  }

  /**
   * Returns the HTTP status for this error.
   *
   * @return the HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }

  /**
   * Returns the SCIM {@code scimType} keyword for this error.
   *
   * @return the SCIM error keyword, or {@code null} if none applies
   */
  public String getScimType() {
    return scimType;
  }
}
