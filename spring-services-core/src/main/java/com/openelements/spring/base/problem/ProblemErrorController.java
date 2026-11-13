package com.openelements.spring.base.problem;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renders errors that never reached a controller.
 *
 * <p>Replaces Spring Boot's {@code BasicErrorController} on {@code server.error.path}. Everything
 * the servlet container produces by itself, and every {@code response.sendError(...)} from a filter,
 * arrives here through the container's {@code ERROR} dispatch — which is the one error path Spring
 * MVC's exception handling never sees.
 *
 * <p>The response is always {@code application/problem+json}, whatever the request asked for. Spring
 * Boot would offer an HTML page to a browser; this library serves APIs, and an HTML error page from
 * an API endpoint is of no use to its caller. Writing the body directly, rather than returning it
 * for content negotiation, is what makes that unconditional.
 *
 * <p>No {@code detail} is set. The text that reaches this point comes from the servlet container or
 * from a {@code sendError(status, message)} somewhere in a filter: it is not curated, is sometimes a
 * stack-trace fragment, and is never written with a client in mind. The status, the type and the
 * trace ID carry everything that can be said responsibly.
 *
 * <p>{@code instance} reports the path the client asked for, taken from {@code
 * jakarta.servlet.error.request_uri}, rather than {@code /error}.
 */
@Controller
class ProblemErrorController implements ErrorController {

  private final ProblemFactory problemFactory;

  private final ProblemResponseWriter responseWriter;

  ProblemErrorController(final ProblemFactory problemFactory, final ObjectMapper objectMapper) {
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
    this.responseWriter = new ProblemResponseWriter(objectMapper);
  }

  /**
   * Answers an error dispatch with a problem.
   *
   * @param request the error dispatch, carrying the original status and path as request attributes
   * @param response the response to write to
   * @throws IOException if the body cannot be written
   */
  @RequestMapping("${server.error.path:${error.path:/error}}")
  public void handleError(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    final int status = statusOf(request);
    final ProblemDetail problem =
        problemFactory.create(StandardProblemType.forStatus(status), null, request);
    // forStatus(...) reports 500 for a code HttpStatus does not know; the dispatch knows the real one.
    problem.setStatus(status);
    responseWriter.write(response, problem);
  }

  /**
   * Reads the status the container is reporting.
   *
   * @param request the error dispatch
   * @return the status code, falling back to {@code 500} when the attribute is missing or unusable
   */
  private static int statusOf(final HttpServletRequest request) {
    final Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (attribute instanceof Integer status && status >= 400 && status <= 599) {
      return status;
    }
    return HttpStatus.INTERNAL_SERVER_ERROR.value();
  }
}
