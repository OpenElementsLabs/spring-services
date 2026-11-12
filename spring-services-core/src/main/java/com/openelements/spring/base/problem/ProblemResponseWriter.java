package com.openelements.spring.base.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Writes a {@link ProblemDetail} onto a raw {@link HttpServletResponse}.
 *
 * <p>Spring MVC serialises a {@code ProblemDetail} returned from a controller or an exception
 * handler on its own. Spring Security's {@code AuthenticationEntryPoint} and {@code
 * AccessDeniedHandler} run in the filter chain, before and outside the {@code DispatcherServlet},
 * and have to write the response themselves — this class is what they use, so a {@code 401} written
 * by a filter is byte-for-byte the same shape as a {@code 409} written by a controller.
 *
 * <p>The response is committed with {@code Content-Type: application/problem+json} regardless of the
 * request's {@code Accept} header. Content negotiation on an error is of no use to anyone: a browser
 * asking for {@code text/html} still gets the problem, because this library serves APIs and has no
 * HTML error pages to offer.
 *
 * <p>The body is built by {@link ProblemBody} rather than serialised from the {@link ProblemDetail}
 * directly, so a problem written here has exactly the same members, in the same order, as one
 * written by the MVC advice — including whether {@code instance} is present at all.
 */
public class ProblemResponseWriter {

  private final ObjectMapper objectMapper;

  /**
   * Creates a writer.
   *
   * @param objectMapper the application's Jackson mapper, so a problem's extension members are
   *     serialised with the application's own configuration
   */
  public ProblemResponseWriter(final ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  /**
   * Writes the problem as the response body.
   *
   * @param response the response to write to
   * @param problem the problem to serialise; its {@code status} becomes the response status
   * @throws IOException if the body cannot be written
   */
  public void write(final HttpServletResponse response, final ProblemDetail problem)
      throws IOException {
    Objects.requireNonNull(response, "response must not be null");
    Objects.requireNonNull(problem, "problem must not be null");

    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ProblemBody.of(problem));
  }
}
