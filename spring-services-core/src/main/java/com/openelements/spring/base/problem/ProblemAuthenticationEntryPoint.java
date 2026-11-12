package com.openelements.spring.base.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers a failed or missing authentication with a {@code 401} problem.
 *
 * <p>Replaces {@code JsonAuthenticationEntryPoint} while {@code
 * openelements.problem-details.enabled} is on. One instance is wired per security filter chain,
 * because the {@code WWW-Authenticate} header is chain-specific — {@code Bearer} on the JWT chain,
 * {@code ApiKey realm="external"} on the external one — and RFC 7235 §3.1 requires a {@code 401} to
 * carry it.
 *
 * <p>The entry point runs in the filter chain, outside the {@code DispatcherServlet}, so it writes
 * the response itself through {@link ProblemResponseWriter}. The result is the same body a
 * controller would have produced, down to the {@code traceId}: the trace filter is ordered ahead of
 * Spring Security precisely so that a {@code 401} is not the one response without one.
 *
 * <p>{@code detail} is the {@link AuthenticationException}'s message, as the previous {@code
 * message} field was. The library only ever feeds short, well-known reasons into this path —
 * {@code "Invalid API key"}, Spring Security's own {@code "Full authentication is required to access
 * this resource"} — and never reflects anything the client sent.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemFactory problemFactory;

  private final ProblemResponseWriter responseWriter;

  private final String wwwAuthenticateHeader;

  /**
   * Creates an entry point for one security filter chain.
   *
   * @param problemFactory builds the problem, so an application that replaced the factory controls
   *     the {@code 401} body too
   * @param responseWriter writes the problem onto the raw response
   * @param wwwAuthenticateHeader the value of the {@code WWW-Authenticate} header — typically
   *     {@code "Bearer"} or {@code "ApiKey realm=\"external\""}
   */
  public ProblemAuthenticationEntryPoint(
      final ProblemFactory problemFactory,
      final ProblemResponseWriter responseWriter,
      final String wwwAuthenticateHeader) {
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    this.wwwAuthenticateHeader =
        Objects.requireNonNull(wwwAuthenticateHeader, "wwwAuthenticateHeader must not be null");
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException {
    final ProblemDetail problem =
        problemFactory.create(
            StandardProblemType.UNAUTHORIZED, authException.getMessage(), request);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticateHeader);
    responseWriter.write(response, problem);
  }
}
