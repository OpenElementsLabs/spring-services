package com.openelements.spring.base.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Answers a denied request with a {@code 403} problem.
 *
 * <p>Wired into the library's filter chains while {@code openelements.problem-details.enabled} is
 * on. Spring Security's default handler calls {@code response.sendError(403)} and leaves the body
 * empty; this one writes the same problem a controller would have produced.
 *
 * <p>It is reached for every kind of denial Spring Security decides on: a rule in the chain itself
 * ({@code denyAll} on the external chain's write methods), a {@code @PreAuthorize} that did not
 * match, and the {@code AccessDeniedException} the user service raises for a deactivated account.
 * All three arrive here through {@code ExceptionTranslationFilter}, which is also what keeps an
 * <em>anonymous</em> caller receiving a {@code 401} from the chain's entry point instead — a
 * distinction the library deliberately leaves with Spring Security rather than reimplementing it in
 * an {@code @ExceptionHandler}.
 *
 * <p>{@code detail} is the exception's message, which the library keeps short and free of anything
 * the client sent ({@code "User account is disabled"}, Spring Security's {@code "Access Denied"}).
 */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemFactory problemFactory;

  private final ProblemResponseWriter responseWriter;

  /**
   * Creates the handler.
   *
   * @param problemFactory builds the problem, so an application that replaced the factory controls
   *     the {@code 403} body too
   * @param responseWriter writes the problem onto the raw response
   */
  public ProblemAccessDeniedHandler(
      final ProblemFactory problemFactory, final ProblemResponseWriter responseWriter) {
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
  }

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AccessDeniedException accessDeniedException)
      throws IOException {
    final ProblemDetail problem =
        problemFactory.create(
            StandardProblemType.FORBIDDEN, accessDeniedException.getMessage(), request);
    responseWriter.write(response, problem);
  }
}
