package com.openelements.spring.base.services.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * SCIM {@code Error} message (RFC 7644 §3.12) returned for every 4xx/5xx response.
 *
 * @param schemas the message schema URNs (the Error URN)
 * @param status the HTTP status code as a string (RFC requires a string)
 * @param scimType the SCIM detail error keyword (e.g. {@code uniqueness}, {@code invalidValue}), or
 *     {@code null} where the RFC defines none for the status
 * @param detail a human-readable error description
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimError(List<String> schemas, String status, String scimType, String detail) {

  /**
   * Builds an {@code Error} with the correct schema URN.
   *
   * @param status the HTTP status code
   * @param scimType the SCIM error keyword, or {@code null}
   * @param detail a human-readable description
   * @return a populated {@code Error} message
   */
  public static ScimError of(final int status, final String scimType, final String detail) {
    return new ScimError(List.of(ScimSchemas.ERROR), Integer.toString(status), scimType, detail);
  }
}
