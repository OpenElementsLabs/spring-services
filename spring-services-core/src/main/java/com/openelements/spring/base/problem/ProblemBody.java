package com.openelements.spring.base.problem;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ProblemDetail;

/**
 * Flattens a {@link ProblemDetail} into the JSON object RFC 9457 describes.
 *
 * <p>Every writer in the library serialises this map rather than the {@code ProblemDetail} itself,
 * for two reasons:
 *
 * <ul>
 *   <li><b>{@code instance} stays optional.</b> Spring MVC fills in a missing {@code instance} with
 *       the request URI when it serialises a {@code ProblemDetail} returned from a handler. That is
 *       helpful by default and impossible to opt out of, so {@code
 *       openelements.problem-details.include-instance=false} would silently have no effect on every
 *       error that goes through Spring MVC — while working for the ones written by the security
 *       filters. Half-applied configuration is worse than none.
 *   <li><b>The shape does not depend on the mapper.</b> Jackson only writes a {@code ProblemDetail}
 *       flat when {@code ProblemDetailJacksonMixin} is registered. Boot's own mapper has it; a
 *       hand-built one does not, and would nest the extension members under a {@code properties}
 *       object. Building the map here removes the question.
 * </ul>
 *
 * <p>Members come out in the order RFC 9457 lists them — {@code type}, {@code title}, {@code
 * status}, {@code detail}, {@code instance} — followed by the extensions in insertion order, so two
 * responses for the same problem are byte-identical. {@code detail} and {@code instance} are omitted
 * rather than written as {@code null}: RFC 9457 §3.1 makes them optional, and {@code "detail": null}
 * says nothing a client can act on.
 */
final class ProblemBody {

  private ProblemBody() {
    throw new AssertionError("ProblemBody must not be instantiated");
  }

  /**
   * Converts a problem into its JSON object form.
   *
   * @param problem the problem to convert
   * @return an unmodifiable map in RFC 9457 member order
   */
  static Map<String, Object> of(final ProblemDetail problem) {
    Objects.requireNonNull(problem, "problem must not be null");

    final Map<String, Object> body = new LinkedHashMap<>();
    // Spring Framework 7 leaves the type null unless set; RFC 9457 defines the absent type as
    // about:blank, which is what Spring Framework 6 put there.
    final URI type = problem.getType();
    body.put("type", type != null ? type.toString() : "about:blank");
    if (problem.getTitle() != null) {
      body.put("title", problem.getTitle());
    }
    body.put("status", problem.getStatus());
    if (problem.getDetail() != null) {
      body.put("detail", problem.getDetail());
    }
    if (problem.getInstance() != null) {
      body.put("instance", problem.getInstance().toString());
    }
    final Map<String, Object> properties = problem.getProperties();
    if (properties != null) {
      body.putAll(properties);
    }
    return Collections.unmodifiableMap(body);
  }
}
