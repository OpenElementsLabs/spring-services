package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.trace.TraceContext;
import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DefaultProblemFactory} — the single place that builds a problem.
 *
 * <h2>What is tested</h2>
 *
 * <p>The member-by-member contract of a problem, because every writer in the library delegates here
 * and nothing downstream re-checks it:
 *
 * <ul>
 *   <li>{@code type}, {@code title} and {@code status} come from the problem type, and the
 *       configured base is honoured — an application that sets a GovStack base gets the library's
 *       own types under it too;
 *   <li>{@code detail} is <b>absent</b>, not empty, when the caller supplied none;
 *   <li>{@code instance} is the request path <b>without the query string</b>. That is a privacy
 *       rule, not a formatting choice: a query string routinely carries e-mail addresses and search
 *       terms, and {@code instance} is echoed to the client and into logs;
 *   <li>on an error dispatch {@code instance} is the original path, not {@code /error}, so the
 *       member names the resource the client asked for;
 *   <li>{@code traceId} matches {@link TraceContext} for the same request;
 *   <li>a {@link ProblemException}'s extension members are copied onto the problem.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with {@link MockHttpServletRequest}; no Spring context.
 *
 * <p><b>Mock-Audit.</b> No behaviour mocks. {@code MockHttpServletRequest} is Spring's servlet-API
 * test double — it implements the API rather than stubbing calls.
 */
@DisplayName("DefaultProblemFactory")
class DefaultProblemFactoryTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private final DefaultProblemFactory factory =
      new DefaultProblemFactory(new ProblemDetailsProperties(true, BASE, true));

  private static MockHttpServletRequest request(final String uri, final String query) {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setQueryString(query);
    return request;
  }

  @Test
  @DisplayName("type, title, status and traceId are always present.")
  void mandatoryMembersArePresent() {
    final MockHttpServletRequest request = request("/api/test/fail", null);

    final ProblemDetail problem =
        factory.create(StandardProblemType.UNAUTHORIZED, "Invalid API key", request);

    assertThat(problem.getType()).hasToString(BASE + "unauthorized");
    assertThat(problem.getTitle()).isEqualTo("Unauthorized");
    assertThat(problem.getStatus()).isEqualTo(401);
    assertThat(problem.getDetail()).isEqualTo("Invalid API key");
    assertThat(problem.getProperties())
        .containsEntry("traceId", TraceContext.traceId(request));
  }

  @Test
  @DisplayName("Without a detail the member is absent rather than empty.")
  void detailIsOmittedWhenNull() {
    final ProblemDetail problem =
        factory.create(StandardProblemType.NOT_FOUND, null, request("/api/test", null));

    assertThat(problem.getDetail()).isNull();
  }

  @Test
  @DisplayName("instance is the request path and never carries the query string.")
  void instanceOmitsTheQueryString() {
    final ProblemDetail problem =
        factory.create(
            StandardProblemType.NOT_FOUND,
            null,
            request("/api/test/fail", "email=jane@example.com"));

    assertThat(problem.getInstance()).hasToString("/api/test/fail");
  }

  @Test
  @DisplayName("include-instance=false removes the member entirely.")
  void instanceCanBeSwitchedOff() {
    final DefaultProblemFactory withoutInstance =
        new DefaultProblemFactory(new ProblemDetailsProperties(true, BASE, false));

    final ProblemDetail problem =
        withoutInstance.create(StandardProblemType.NOT_FOUND, null, request("/api/test", null));

    assertThat(problem.getInstance()).isNull();
  }

  @Test
  @DisplayName("On an error dispatch instance is the original path, not /error.")
  void instanceUsesTheOriginalPathOnErrorDispatch() {
    final MockHttpServletRequest request = request("/error", null);
    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/test/limited");

    final ProblemDetail problem =
        factory.create(StandardProblemType.TOO_MANY_REQUESTS, null, request);

    assertThat(problem.getInstance()).hasToString("/api/test/limited");
  }

  @Test
  @DisplayName("A configured base applies to the library's types and the application's alike.")
  void configuredBaseAppliesToAllTypes() {
    final String govStackBase = "https://govstack.global/problems/registries/";
    final DefaultProblemFactory govStack =
        new DefaultProblemFactory(new ProblemDetailsProperties(true, govStackBase, true));
    final ProblemType applicationType =
        ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact");
    final MockHttpServletRequest request = request("/api/contacts", null);

    assertThat(govStack.create(StandardProblemType.UNAUTHORIZED, null, request).getType())
        .hasToString(govStackBase + "unauthorized");
    assertThat(govStack.create(applicationType, null, request).getType())
        .hasToString(govStackBase + "duplicate-contact");
  }

  @Test
  @DisplayName("A ProblemException contributes its detail and its extension members.")
  void problemExceptionContributesItsMembers() {
    final ProblemType type =
        ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact");
    final ProblemException exception =
        new ProblemException(
            type, "A contact with this e-mail address already exists", Map.of("existingId", "42"));

    final ProblemDetail problem = factory.create(exception, request("/api/contacts", null));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getType()).hasToString(BASE + "duplicate-contact");
    assertThat(problem.getDetail()).isEqualTo("A contact with this e-mail address already exists");
    assertThat(problem.getProperties()).containsEntry("existingId", "42");
  }

  @Test
  @DisplayName("Repeated problems for one request report the same traceId.")
  void traceIdIsStableWithinARequest() {
    final MockHttpServletRequest request = request("/api/test", null);

    final Object first =
        factory.create(StandardProblemType.NOT_FOUND, null, request).getProperties().get("traceId");
    final Object second =
        factory
            .create(StandardProblemType.INTERNAL_ERROR, null, request)
            .getProperties()
            .get("traceId");

    assertThat(first).asString().matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
    assertThat(second).isEqualTo(first);
  }
}
