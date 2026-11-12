package com.openelements.spring.base.problem;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 9457 problems this library writes.
 *
 * <p>Every problem goes through here — the MVC exception handler, the catch-all resolver, the error
 * controller, the two security handlers and the ALTCHA module — so the members, and the rules for
 * what goes into them, live in one place.
 *
 * <p>An application injects it to build problems in its own {@code @ExceptionHandler}:
 *
 * <pre>{@code
 * @RestControllerAdvice
 * class SdkExceptionHandler {
 *
 *   private final ProblemFactory problems;
 *
 *   SdkExceptionHandler(ProblemFactory problems) {
 *     this.problems = problems;
 *   }
 *
 *   @ExceptionHandler(SdkTimeoutException.class)
 *   ProblemDetail handle(SdkTimeoutException e, HttpServletRequest request) {
 *     return problems.create(StandardProblemType.SERVICE_UNAVAILABLE, "Upstream timed out", request);
 *   }
 * }
 * }</pre>
 *
 * <p>An application that needs a different shape — an extra member on every problem, say — declares
 * its own bean of this type and the library's default backs off. All five writers then use it,
 * including the ones that run outside Spring MVC.
 */
public interface ProblemFactory {

  /**
   * Builds a problem of the given type.
   *
   * @param type the problem type; supplies {@code type}, {@code title} and {@code status}
   * @param detail the occurrence-specific explanation, or {@code null} for none. Written to the
   *     client verbatim, so it must not carry personal data or internal detail
   * @param request the request being handled; supplies {@code instance} and {@code traceId}
   * @return the problem, ready to be serialised
   */
  ProblemDetail create(ProblemType type, @Nullable String detail, HttpServletRequest request);

  /**
   * Builds the problem described by a {@link ProblemException}, including its extension members.
   *
   * @param exception the thrown problem
   * @param request the request being handled; supplies {@code instance} and {@code traceId}
   * @return the problem, ready to be serialised
   */
  ProblemDetail create(ProblemException exception, HttpServletRequest request);
}
