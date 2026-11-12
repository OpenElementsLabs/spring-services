package com.openelements.spring.base.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

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
 * <p>The mapper is copied once at construction and the {@link ProblemDetailJacksonMixin} is applied
 * to the copy. Without that mixin Jackson serialises the extension members as a nested {@code
 * properties} object instead of as top-level members, which is not RFC 9457. Spring Boot's
 * auto-configured mapper already carries the mixin, but this class is also constructed from mappers
 * that do not — and a {@code 401} that silently changes shape is exactly the kind of difference
 * nobody notices until a client breaks. Copying leaves the application's own mapper untouched.
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
    Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.objectMapper =
        objectMapper.copy().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
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
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
