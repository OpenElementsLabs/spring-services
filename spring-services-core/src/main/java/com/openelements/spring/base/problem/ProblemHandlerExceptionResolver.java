package com.openelements.spring.base.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * The last resort: turns anything still unhandled into a problem.
 *
 * <p>Registered directly <em>after</em> Spring's {@code ExceptionHandlerExceptionResolver}, so every
 * {@code @ExceptionHandler} in the application — with or without an {@code @Order} — has already had
 * its chance. That position is the whole point of this class being a resolver rather than another
 * {@code @ControllerAdvice}: two advices without an explicit order both sit at {@code
 * LOWEST_PRECEDENCE} and the winner depends on bean registration order, which is not a contract. A
 * resolver placed after the advice resolver can never shadow an application handler.
 *
 * <p>It also avoids a trap: a {@code @ExceptionHandler(Exception.class)} in an advice runs before
 * {@code ResponseStatusExceptionResolver}, so it would turn every {@code @ResponseStatus}-annotated
 * exception into a {@code 500}. This resolver runs before that resolver too, and therefore honours
 * {@code @ResponseStatus} itself.
 *
 * <h2>Resolution order</h2>
 *
 * <ol>
 *   <li>an exception annotated {@link ResponseStatus} → that status, its {@code reason} as {@code
 *       detail};
 *   <li>an {@link ErrorResponse} — {@code ResponseStatusException} and friends — → its status and
 *       its detail;
 *   <li>a {@link ConstraintViolationException} raised at the controller boundary → a {@code 400};
 *   <li>anything else → {@code 500 internal-error} with a fixed detail.
 * </ol>
 *
 * <h2>What it deliberately does not resolve</h2>
 *
 * <p>{@link AccessDeniedException}, including Spring Security's {@code AuthorizationDeniedException}
 * from method security. Resolving it here would take the decision away from Spring Security's {@code
 * ExceptionTranslationFilter}, which answers {@code 401} for an anonymous caller and {@code 403} for
 * an authenticated one. Letting it propagate keeps that distinction and still produces a problem
 * body, written by {@code ProblemAccessDeniedHandler} or the chain's authentication entry point.
 *
 * <h2>What a {@code 500} tells the client</h2>
 *
 * <p>Nothing but its type. The detail is the fixed sentence {@code An unexpected error occurred.};
 * the exception's message, its class name and its stack trace stay in the log. An unexpected
 * exception is where SQL fragments, file paths and personal data surface, and the client has no use
 * for any of it — the {@code traceId} is what connects their report to the log entry.
 */
final class ProblemHandlerExceptionResolver implements HandlerExceptionResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemHandlerExceptionResolver.class);

  /** The only text a client ever learns about an unexpected failure. */
  static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred.";

  private final ProblemFactory problemFactory;

  private final ProblemResponseWriter responseWriter;

  ProblemHandlerExceptionResolver(
      final ProblemFactory problemFactory, final ObjectMapper objectMapper) {
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
    this.responseWriter = new ProblemResponseWriter(objectMapper);
  }

  @Override
  public @Nullable ModelAndView resolveException(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final @Nullable Object handler,
      final Exception ex) {
    if (ex instanceof AccessDeniedException) {
      return null;
    }
    final ProblemDetail problem = toProblem(request, handler, ex);
    log(problem, ex);
    try {
      responseWriter.write(response, problem);
    } catch (final IOException e) {
      LOG.warn("Could not write the problem response: {}", e.getMessage());
      LOG.debug("Could not write the problem response", e);
      return null;
    }
    return new ModelAndView();
  }

  /**
   * Chooses the problem for an exception.
   *
   * @param request the request being handled
   * @param handler the handler the exception came from, if known
   * @param ex the exception
   * @return the problem to write
   */
  private ProblemDetail toProblem(
      final HttpServletRequest request, final @Nullable Object handler, final Exception ex) {
    if (ex instanceof ProblemException problemException) {
      return problemFactory.create(problemException, request);
    }
    final ResponseStatus responseStatus =
        AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
    if (responseStatus != null) {
      final String reason = responseStatus.reason();
      return problemFactory.create(
          StandardProblemType.forStatus(responseStatus.code().value()),
          reason.isBlank() ? null : reason,
          request);
    }
    if (ex instanceof ErrorResponse errorResponse) {
      final int status = errorResponse.getStatusCode().value();
      final ProblemDetail problem =
          problemFactory.create(
              StandardProblemType.forStatus(status), errorResponse.getBody().getDetail(), request);
      // forStatus(...) reports 500 for a code HttpStatus does not know; the occurrence knows better.
      problem.setStatus(status);
      return problem;
    }
    if (ex instanceof ConstraintViolationException violations
        && isControllerBoundary(violations, handler)) {
      return problemFactory.create(
          StandardProblemType.INVALID_PARAMETER, describe(violations), request);
    }
    return problemFactory.create(
        StandardProblemType.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL, request);
  }

  /**
   * Decides whether a {@link ConstraintViolationException} describes the request or a bug.
   *
   * <p>It describes the request only when <em>every</em> violation was raised on the handler method
   * the request is being dispatched to — the shape produced by a controller annotated {@code
   * @Validated}. A violation from a {@code @Validated} service deeper down means the application
   * built an invalid argument itself: that is a server-side defect, and answering {@code 400} would
   * blame the client for it and hide the bug.
   *
   * @param violations the exception
   * @param handler the handler the request was dispatched to
   * @return {@code true} if the exception came from the controller boundary
   */
  private static boolean isControllerBoundary(
      final ConstraintViolationException violations, final @Nullable Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return false;
    }
    final var constraintViolations = violations.getConstraintViolations();
    if (constraintViolations == null || constraintViolations.isEmpty()) {
      return false;
    }
    return constraintViolations.stream()
        .allMatch(violation -> raisedOn(violation, handlerMethod));
  }

  /**
   * Checks that one violation was raised on the given handler method.
   *
   * @param violation the violation
   * @param handlerMethod the handler the request was dispatched to
   * @return {@code true} if the violation's root bean and executable are the handler's
   */
  private static boolean raisedOn(
      final ConstraintViolation<?> violation, final HandlerMethod handlerMethod) {
    final Object rootBean = violation.getRootBean();
    if (rootBean == null || !handlerMethod.getBeanType().isInstance(rootBean)) {
      return false;
    }
    for (final Path.Node node : violation.getPropertyPath()) {
      if (node.getKind() == ElementKind.METHOD) {
        return handlerMethod.getMethod().getName().equals(node.getName());
      }
    }
    return false;
  }

  /**
   * Renders the violations of a controller-boundary failure as parameter sentences.
   *
   * @param violations the exception
   * @return the joined sentences, or {@code null} when there is nothing to say
   */
  private static @Nullable String describe(final ConstraintViolationException violations) {
    final List<String> sentences = new ArrayList<>();
    for (final ConstraintViolation<?> violation : violations.getConstraintViolations()) {
      sentences.add(
          ParameterMessages.invalid(
              "request", parameterName(violation.getPropertyPath()), violation.getMessage()));
    }
    return ParameterMessages.join(sentences);
  }

  /**
   * Reads the parameter name out of a method-validation path.
   *
   * @param path the property path of the violation
   * @return the last segment of the path, which names the parameter
   */
  private static String parameterName(final Path path) {
    String name = "";
    for (final Path.Node node : path) {
      if (node.getKind() != ElementKind.METHOD && node.getName() != null) {
        name = node.getName();
      }
    }
    return name;
  }

  /**
   * Logs the problem: server errors loudly with the exception, client errors quietly.
   *
   * <p>The trace ID is already in the MDC, put there by the trace filter, so the log line and the
   * response body can be matched up afterwards.
   *
   * @param problem the problem being written
   * @param ex the exception that produced it
   */
  private static void log(final ProblemDetail problem, final Exception ex) {
    if (problem.getStatus() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
      LOG.error("Request failed with status {}", problem.getStatus(), ex);
    } else if (LOG.isDebugEnabled()) {
      LOG.debug("Request rejected with status {}: {}", problem.getStatus(), ex.toString());
    }
  }
}
