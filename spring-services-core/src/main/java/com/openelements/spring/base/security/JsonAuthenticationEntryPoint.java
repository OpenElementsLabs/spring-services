package com.openelements.spring.base.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Uniform JSON {@link AuthenticationEntryPoint} for both security filter chains.
 *
 * <p>Produces a stable, machine-parseable response body on every {@code 401 Unauthorized}:
 *
 * <pre>{@code
 * { "status": 401, "error": "Unauthorized", "message": "<reason>" }
 * }</pre>
 *
 * <p>Two beans of this type are wired in {@link SecurityConfig}: one for the JWT chain (with
 * {@code WWW-Authenticate: Bearer}) and one for the external API-key chain (with
 * {@code WWW-Authenticate: ApiKey realm="external"}). The scheme value is constructor-injected so
 * the same class serves both chains and stays RFC 7235 §3.1 compliant.
 *
 * <p>The {@code message} field carries the {@link AuthenticationException#getMessage()} of the
 * triggering exception. The library only feeds short, well-known reason strings into this path
 * ({@code "Invalid API key"}, Spring Security defaults like {@code "Full authentication is
 * required to access this resource"}) — no user-supplied content is reflected.
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final String wwwAuthenticateHeader;

  /**
   * @param objectMapper the application's Jackson mapper, used to write the JSON body
   * @param wwwAuthenticateHeader the value to send in the {@code WWW-Authenticate} response
   *     header — typically {@code "Bearer"} for the JWT chain or
   *     {@code "ApiKey realm=\"external\""} for the API-key chain
   */
  public JsonAuthenticationEntryPoint(
      final ObjectMapper objectMapper, final String wwwAuthenticateHeader) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.wwwAuthenticateHeader =
        Objects.requireNonNull(wwwAuthenticateHeader, "wwwAuthenticateHeader must not be null");
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticateHeader);

    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", HttpStatus.UNAUTHORIZED.value());
    body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
    body.put("message", authException.getMessage());

    objectMapper.writeValue(response.getWriter(), body);
  }
}
