package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link ProblemResponseWriter} — the writer used outside Spring MVC.
 *
 * <h2>What is tested</h2>
 *
 * <p>That a problem written by a Spring Security filter is indistinguishable from one written by
 * the {@code DispatcherServlet}: the same {@code application/problem+json} content type, the same
 * status on the status line as in the body, and the extension members serialised as top-level
 * members rather than nested under a {@code properties} object — which is what {@code
 * ProblemDetail}'s {@code @JsonAnyGetter} is for, and what a client parsing RFC 9457 expects.
 *
 * <p>The content type is asserted explicitly because the entry point runs before content
 * negotiation: nothing else would catch it falling back to {@code application/json}.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with {@link MockHttpServletResponse} and a real {@link ObjectMapper}; the JSON
 * that comes out is the JSON a client would receive. No mocks.
 */
@DisplayName("ProblemResponseWriter")
class ProblemResponseWriterTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private final ProblemResponseWriter writer = new ProblemResponseWriter(new JsonMapper());
  private final DefaultProblemFactory factory =
      new DefaultProblemFactory(new ProblemDetailsProperties(true, BASE, true));

  @Test
  @DisplayName("The response carries the problem's status and application/problem+json.")
  void statusAndContentTypeAreWritten() throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final ProblemDetail problem =
        factory.create(
            StandardProblemType.UNAUTHORIZED,
            "Invalid API key",
            new MockHttpServletRequest("GET", "/api/external/contacts"));

    writer.write(response, problem);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
  }

  @Test
  @DisplayName("The body is a flat RFC 9457 object — traceId is a top-level member.")
  void bodyIsAFlatProblemObject() throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final ProblemDetail problem =
        factory.create(
            StandardProblemType.UNAUTHORIZED,
            "Invalid API key",
            new MockHttpServletRequest("GET", "/api/external/contacts"));

    writer.write(response, problem);

    final var body = new JsonMapper().readTree(response.getContentAsByteArray());
    assertThat(body.path("type").asString()).isEqualTo(BASE + "unauthorized");
    assertThat(body.path("title").asString()).isEqualTo("Unauthorized");
    assertThat(body.path("status").asInt()).isEqualTo(401);
    assertThat(body.path("detail").asString()).isEqualTo("Invalid API key");
    assertThat(body.path("instance").asString()).isEqualTo("/api/external/contacts");
    assertThat(body.path("traceId").asString()).matches("[0-9a-f]{32}");
    assertThat(body.has("properties")).isFalse();
  }

  @Test
  @DisplayName("Null arguments are rejected.")
  void nullArgumentsRejected() {
    assertThatThrownBy(() -> new ProblemResponseWriter(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> writer.write(new MockHttpServletResponse(), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> writer.write(null, ProblemDetail.forStatus(org.springframework.http.HttpStatus.NOT_FOUND)))
        .isInstanceOf(NullPointerException.class);
  }
}
