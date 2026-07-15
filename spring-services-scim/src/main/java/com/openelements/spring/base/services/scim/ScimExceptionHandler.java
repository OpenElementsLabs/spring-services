package com.openelements.spring.base.services.scim;

import com.openelements.spring.base.services.scim.model.ScimError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders every {@link ScimException} thrown by the SCIM controllers as an RFC 7644 {@code Error}
 * envelope with the matching HTTP status and {@code application/scim+json} content type. Scoped by
 * {@code basePackageClasses} to the SCIM package so it never intercepts other controllers' errors.
 */
@RestControllerAdvice(basePackageClasses = ScimExceptionHandler.class)
public class ScimExceptionHandler {

  /** Creates the SCIM exception handler. */
  public ScimExceptionHandler() {}

  /**
   * Maps a {@link ScimException} to a SCIM {@code Error} response.
   *
   * @param exception the SCIM exception to render
   * @return a response carrying the SCIM {@code Error} envelope and the exception's status
   */
  @ExceptionHandler(ScimException.class)
  public ResponseEntity<ScimError> handleScimException(final ScimException exception) {
    return ResponseEntity.status(exception.getStatus())
        .contentType(MediaType.parseMediaType(ScimMediaType.SCIM_JSON))
        .body(
            ScimError.of(
                exception.getStatus().value(), exception.getScimType(), exception.getMessage()));
  }
}
