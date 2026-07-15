package com.openelements.spring.base.services.scim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openelements.spring.base.services.scim.model.ScimError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * SCIM-shaped authentication entry point: renders a {@code 401 Unauthorized} with an
 * {@code application/scim+json} {@link ScimError} envelope and a {@code WWW-Authenticate: Bearer}
 * header when a {@code /scim/v2/**} request is unauthenticated. Keeps the SCIM error surface
 * consistent with RFC 7644 rather than leaking the default Spring Security HTML/JSON body.
 */
@Component
public class ScimAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  /**
   * Creates the entry point.
   *
   * @param objectMapper the mapper used to serialise the {@link ScimError} body
   */
  public ScimAuthenticationEntryPoint(final ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(ScimMediaType.SCIM_JSON);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    final ScimError error =
        ScimError.of(HttpStatus.UNAUTHORIZED.value(), null, "Authentication required");
    objectMapper.writeValue(response.getWriter(), error);
  }
}
