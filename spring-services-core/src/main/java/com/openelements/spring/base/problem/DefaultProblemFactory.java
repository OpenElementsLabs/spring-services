package com.openelements.spring.base.problem;

import com.openelements.spring.base.trace.TraceContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * The {@link ProblemFactory} shipped with the library.
 *
 * <p>Fills the members RFC 9457 defines, plus the {@code traceId} the GovStack {@code Problem}
 * schema requires:
 *
 * <ul>
 *   <li>{@code type} — the configured base URI with the problem type's slug appended;
 *   <li>{@code title} and {@code status} — from the problem type;
 *   <li>{@code detail} — only when the caller supplied one. RFC 9457 §3.1.4 makes it optional, and
 *       an empty or invented detail is worse than none;
 *   <li>{@code instance} — the request path when {@code include-instance} is on. On an error
 *       dispatch the path of the <em>original</em> request is used, not {@code /error}, so the
 *       member names the resource the client asked for. The query string is never included, because
 *       it may carry personal data;
 *   <li>{@code traceId} — from {@link TraceContext}, so a 5xx logged at {@code ERROR} and the body
 *       the client receives carry the same ID;
 *   <li>the extension members of a {@link ProblemException}.
 * </ul>
 */
final class DefaultProblemFactory implements ProblemFactory {

  private final ProblemDetailsProperties properties;

  DefaultProblemFactory(final ProblemDetailsProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  public ProblemDetail create(
      final ProblemType type, final @Nullable String detail, final HttpServletRequest request) {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(request, "request must not be null");

    final ProblemDetail problem = ProblemDetail.forStatus(type.status());
    problem.setType(properties.typeUri(type));
    problem.setTitle(type.title());
    if (detail != null) {
      problem.setDetail(detail);
    }
    if (properties.includeInstance()) {
      final URI instance = instance(request);
      if (instance != null) {
        problem.setInstance(instance);
      }
    }
    problem.setProperty(ProblemMembers.TRACE_ID, TraceContext.traceId(request));
    return problem;
  }

  @Override
  public ProblemDetail create(
      final ProblemException exception, final HttpServletRequest request) {
    Objects.requireNonNull(exception, "exception must not be null");

    final ProblemDetail problem =
        create(exception.type(), exception.detail().orElse(null), request);
    exception.properties().forEach(problem::setProperty);
    return problem;
  }

  /**
   * Determines the path to report as {@code instance}.
   *
   * <p>A path that cannot be parsed as a URI reference yields no {@code instance} at all. This is
   * the error renderer: it runs when something has already gone wrong, and throwing here would
   * replace a considered {@code 404} with an unconsidered {@code 500} and lose the original failure.
   * One optional member is the cheaper loss.
   *
   * @param request the request being handled
   * @return the original request path on an error dispatch, the current path otherwise — never with
   *     a query string; {@code null} if it cannot be expressed as a URI
   */
  private static @Nullable URI instance(final HttpServletRequest request) {
    final Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    final String path = original instanceof String uri ? uri : request.getRequestURI();
    if (path == null) {
      return null;
    }
    try {
      return URI.create(path);
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }
}
