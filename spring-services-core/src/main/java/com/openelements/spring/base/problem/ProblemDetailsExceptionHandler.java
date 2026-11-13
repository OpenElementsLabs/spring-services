package com.openelements.spring.base.problem;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns Spring MVC's own exceptions into RFC 9457 problems.
 *
 * <p>Registered only when the application declares no {@link ResponseEntityExceptionHandler} of its
 * own. An application that extends that class takes over this job entirely; the catch-all {@code
 * ProblemHandlerExceptionResolver} still runs behind it, so unexpected exceptions keep producing a
 * problem either way.
 *
 * <p>Ordered at {@link Ordered#LOWEST_PRECEDENCE}, so any application {@code @ControllerAdvice}
 * wins. An application advice that handles one of the exceptions below — {@code
 * MethodArgumentNotValidException}, say — has the <em>same</em> order by default, and which of two
 * equally ordered advices wins depends on bean registration order, which is not a contract.
 * Such an advice must therefore carry an explicit {@code @Order}.
 *
 * <h2>What it maps</h2>
 *
 * <table border="1">
 *   <caption>Exception to problem type</caption>
 *   <tr><th>Exception</th><th>Problem</th></tr>
 *   <tr><td>{@code MethodArgumentNotValidException}</td>
 *       <td>{@code validation-failed} with {@code errors[]}</td></tr>
 *   <tr><td>{@code HandlerMethodValidationException}</td>
 *       <td>{@code validation-failed} or {@code invalid-parameter}, see below</td></tr>
 *   <tr><td>{@code HttpMessageNotReadableException}</td>
 *       <td>{@code malformed-request}, generic detail</td></tr>
 *   <tr><td>type mismatch, missing parameter, missing header</td>
 *       <td>{@code invalid-parameter}, value never echoed</td></tr>
 *   <tr><td>every other standard MVC exception</td>
 *       <td>{@code StandardProblemType.forStatus(status)}, response headers kept</td></tr>
 * </table>
 *
 * <h2>Where validation failures go</h2>
 *
 * <p>Failures on the request <em>body</em> become {@code errors} entries with a JSON pointer.
 * Failures on query, path and header parameters are described in {@code detail} instead, because a
 * pointer addresses a location in a JSON document and a parameter has none. A request that gets both
 * wrong is a {@code validation-failed} carrying the body's {@code errors} and the parameters'
 * sentences:
 *
 * <table border="1">
 *   <caption>Which type a validation failure produces</caption>
 *   <tr><th>Failures</th><th>Type</th><th>Members</th></tr>
 *   <tr><td>body only</td><td>{@code validation-failed}</td><td>{@code errors[]}</td></tr>
 *   <tr><td>parameters only</td><td>{@code invalid-parameter}</td><td>{@code detail}</td></tr>
 *   <tr><td>both</td><td>{@code validation-failed}</td><td>{@code errors[]} and {@code detail}</td></tr>
 * </table>
 *
 * <h2>What never reaches the client</h2>
 *
 * <p>No rejected value, no Jackson parser message and no class name. Spring's own detail for a type
 * mismatch is {@code Failed to convert 'size' with value: 'jane@example.com'}; this handler replaces
 * it. For the exceptions it does not override, Spring's detail is kept — those messages name the
 * offending header or method, not the payload.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

  private final ProblemFactory problemFactory;

  private final BodyErrors bodyErrors;

  /**
   * Creates the handler.
   *
   * @param problemFactory builds every problem this handler returns
   * @param objectMapper the application's Jackson mapper, used to resolve the JSON names a
   *     validation pointer is built from
   */
  public ProblemDetailsExceptionHandler(
      final ProblemFactory problemFactory, final ObjectMapper objectMapper) {
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
    this.bodyErrors = new BodyErrors(new JsonPointers(objectMapper));
  }

  // --- validation -------------------------------------------------------------------------------

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      final MethodArgumentNotValidException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    final ProblemDetail problem =
        problem(StandardProblemType.VALIDATION_FAILED, null, request);
    problem.setProperty(ProblemMembers.ERRORS, bodyErrors.from(ex.getBindingResult()));
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * Handles the failure Spring MVC's built-in method validation raises, which may describe the
   * request body and its parameters at the same time.
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      final HandlerMethodValidationException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    final List<Map<String, String>> errors = new ArrayList<>();
    final List<String> parameterSentences = new ArrayList<>();

    for (final ParameterValidationResult result : ex.getParameterValidationResults()) {
      if (isRequestBody(result.getMethodParameter())
          && result instanceof org.springframework.validation.method.ParameterErrors parameterErrors) {
        errors.addAll(bodyErrors.from(parameterErrors));
      } else {
        parameterSentences.addAll(sentencesFor(result));
      }
    }

    final String detail = ParameterMessages.join(parameterSentences);
    final ProblemType type =
        errors.isEmpty() ? StandardProblemType.INVALID_PARAMETER : StandardProblemType.VALIDATION_FAILED;
    final ProblemDetail problem = problem(type, detail, request);
    if (!errors.isEmpty()) {
      problem.setProperty(ProblemMembers.ERRORS, List.copyOf(errors));
    }
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  // --- malformed and unusable input ---------------------------------------------------------------

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      final HttpMessageNotReadableException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    final ProblemDetail problem =
        problem(
            StandardProblemType.MALFORMED_REQUEST,
            "The request body could not be read as JSON.",
            request);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      final TypeMismatchException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    final String detail;
    if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
      final MethodParameter parameter = mismatch.getParameter();
      detail =
          ParameterMessages.typeMismatch(
              ParameterMessages.location(parameter),
              ParameterMessages.name(parameter),
              mismatch.getRequiredType());
    } else {
      detail = "A request value could not be converted to the expected type.";
    }
    final ProblemDetail problem = problem(StandardProblemType.INVALID_PARAMETER, detail, request);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      final MissingServletRequestParameterException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    final ProblemDetail problem =
        problem(
            StandardProblemType.INVALID_PARAMETER,
            ParameterMessages.missing("query", ex.getParameterName()),
            request);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      final org.springframework.web.bind.ServletRequestBindingException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    if (ex instanceof MissingRequestHeaderException missingHeader) {
      final ProblemDetail problem =
          problem(
              StandardProblemType.INVALID_PARAMETER,
              ParameterMessages.missing("header", missingHeader.getHeaderName()),
              request);
      return handleExceptionInternal(ex, problem, headers, status, request);
    }
    return super.handleServletRequestBindingException(ex, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleMissingPathVariable(
      final org.springframework.web.bind.MissingPathVariableException ex,
      final HttpHeaders headers,
      final HttpStatusCode status,
      final WebRequest request) {
    final ProblemDetail problem =
        problem(
            StandardProblemType.INVALID_PARAMETER,
            ParameterMessages.missing("path", ex.getVariableName()),
            request);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  // --- everything else --------------------------------------------------------------------------

  /**
   * Converts the body of every standard MVC exception into a problem.
   *
   * <p>The base class has already decided the status and the response headers — {@code Allow} on a
   * {@code 405}, {@code Accept} on a {@code 415} — and those are kept. Only the body is replaced, by
   * the problem for that status, carrying Spring's own detail when it has one. Spring's details for
   * these exceptions name the method, the media type or the header, never the payload.
   *
   * <p>The overrides above pass their own {@link ProblemDetail} as {@code body}; it is used as is.
   *
   * <p>The response carries {@link ProblemBody#of(ProblemDetail) the problem's map form} and an
   * explicit {@code application/problem+json} content type, rather than the {@code ProblemDetail}
   * itself. Returning the object would hand it to Spring MVC, which fills in a missing {@code
   * instance} with the request URI — and {@code include-instance=false} would then work for the
   * problems written by the security filters and silently not for these.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      final Exception ex,
      final @Nullable Object body,
      final HttpHeaders headers,
      final HttpStatusCode statusCode,
      final WebRequest request) {
    final ProblemDetail problem =
        body instanceof ProblemDetail detail ? detail : asProblem(ex, statusCode, request);
    final HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.addAll(headers);
    responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return super.handleExceptionInternal(
        ex, ProblemBody.of(problem), responseHeaders, statusCode, request);
  }

  /**
   * Builds the problem for an exception this handler does not map explicitly.
   *
   * @param ex the exception
   * @param statusCode the status the base class chose
   * @param request the request being handled
   * @return the problem for that status, carrying Spring's detail when there is one
   */
  private ProblemDetail asProblem(
      final Exception ex, final HttpStatusCode statusCode, final WebRequest request) {
    final String detail = ex instanceof ErrorResponse errorResponse ? errorResponse.getBody().getDetail() : null;
    return problem(StandardProblemType.forStatus(statusCode.value()), detail, request);
  }

  /**
   * Builds a problem for the current request.
   *
   * @param type the problem type
   * @param detail the occurrence-specific explanation, or {@code null}
   * @param request the request being handled
   * @return the problem
   */
  private ProblemDetail problem(
      final ProblemType type, final @Nullable String detail, final WebRequest request) {
    return problemFactory.create(type, detail, servletRequest(request));
  }

  /**
   * Unwraps the servlet request the factory needs for {@code instance} and {@code traceId}.
   *
   * @param request the Spring {@link WebRequest}
   * @return the underlying servlet request
   * @throws IllegalStateException if the request is not a servlet request — this handler is
   *     registered only in servlet web applications
   */
  private static HttpServletRequest servletRequest(final WebRequest request) {
    final HttpServletRequest servletRequest =
        request instanceof NativeWebRequest nativeRequest
            ? nativeRequest.getNativeRequest(HttpServletRequest.class)
            : null;
    if (servletRequest == null) {
      throw new IllegalStateException(
          "ProblemDetailsExceptionHandler requires a servlet request but got " + request.getClass());
    }
    return servletRequest;
  }

  /**
   * Renders the failures reported for one handler-method parameter.
   *
   * @param result the validation result of one parameter
   * @return one sentence per failure
   */
  private static List<String> sentencesFor(final ParameterValidationResult result) {
    final MethodParameter parameter = result.getMethodParameter();
    final String location = ParameterMessages.location(parameter);
    final String name = ParameterMessages.name(parameter);
    return result.getResolvableErrors().stream()
        .map(error -> ParameterMessages.invalid(location, name, error.getDefaultMessage()))
        .toList();
  }

  /**
   * Decides whether a handler-method parameter is the request body.
   *
   * @param parameter the handler-method parameter
   * @return {@code true} if the parameter is annotated {@code @RequestBody}
   */
  private static boolean isRequestBody(final MethodParameter parameter) {
    return parameter.hasParameterAnnotation(RequestBody.class);
  }
}
