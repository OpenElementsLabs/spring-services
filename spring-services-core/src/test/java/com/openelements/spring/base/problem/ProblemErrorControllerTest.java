package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link ProblemErrorController} — the renderer for errors that never reached a
 * controller.
 *
 * <h2>What is tested</h2>
 *
 * <p>The three decisions that make this controller different from Spring Boot's:
 *
 * <ul>
 *   <li><b>The response is a problem even for a browser.</b> {@code Accept: text/html} still gets
 *       {@code application/problem+json}. Boot would serve an HTML page here; an API has none to
 *       offer, and a caller parsing JSON must not suddenly receive markup.
 *   <li><b>{@code instance} is the path the client asked for</b>, not {@code /error}. The dispatch
 *       happens on {@code /error}, so without reading {@code jakarta.servlet.error.request_uri}
 *       every container error would claim to have occurred at the same place.
 *   <li><b>There is no {@code detail}.</b> The container's message is not written for a client and
 *       is sometimes a stack-trace fragment.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Spring's servlet-API test doubles, parsing the JSON that is actually
 * written. No mocks and no Spring context — the controller takes only a factory and a mapper.
 */
@DisplayName("ProblemErrorController")
class ProblemErrorControllerTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProblemErrorController controller =
      new ProblemErrorController(
          new DefaultProblemFactory(new ProblemDetailsProperties(true, BASE, true)), objectMapper);

  private MockHttpServletRequest errorDispatch(final Integer status, final String originalUri) {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    if (status != null) {
      request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
    }
    if (originalUri != null) {
      request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, originalUri);
    }
    return request;
  }

  private JsonNode render(final MockHttpServletRequest request, final MockHttpServletResponse response)
      throws Exception {
    controller.handleError(request, response);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    return objectMapper.readTree(response.getContentAsByteArray());
  }

  @ParameterizedTest(name = "[{index}] {0} → {1}")
  @CsvSource({
    "404, not-found",
    "403, forbidden",
    "429, too-many-requests",
    "500, internal-error",
    "503, service-unavailable",
  })
  @DisplayName("The container's status decides the type.")
  void statusDecidesTheType(final int status, final String slug) throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();

    final JsonNode body = render(errorDispatch(status, "/api/test/limited"), response);

    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("type").asText()).isEqualTo(BASE + slug);
    assertThat(body.path("traceId").asText()).matches("[0-9a-f]{32}");
  }

  @Test
  @DisplayName("instance reports the original path, not /error.")
  void instanceIsTheOriginalPath() throws Exception {
    final JsonNode body =
        render(errorDispatch(429, "/api/test/limited"), new MockHttpServletResponse());

    assertThat(body.path("instance").asText()).isEqualTo("/api/test/limited");
  }

  @Test
  @DisplayName("No detail is written — the container's message is not curated.")
  void noDetailIsWritten() throws Exception {
    final JsonNode body = render(errorDispatch(404, "/api/missing"), new MockHttpServletResponse());

    assertThat(body.has("detail")).isFalse();
  }

  @Test
  @DisplayName("Accept: text/html still receives a problem, not an HTML page.")
  void acceptHtmlStillReceivesAProblem() throws Exception {
    final MockHttpServletRequest request = errorDispatch(404, "/api/missing");
    request.addHeader(HttpHeaders.ACCEPT, "text/html");
    final MockHttpServletResponse response = new MockHttpServletResponse();

    final JsonNode body = render(request, response);

    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(body.path("type").asText()).isEqualTo(BASE + "not-found");
  }

  @Test
  @DisplayName("A status Spring cannot name keeps its own code.")
  void unknownStatusKeepsItsCode() throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();

    final JsonNode body = render(errorDispatch(499, "/api/test"), response);

    assertThat(response.getStatus()).isEqualTo(499);
    assertThat(body.path("status").asInt()).isEqualTo(499);
    assertThat(body.path("type").asText()).isEqualTo(BASE + "http-499");
  }

  @Test
  @DisplayName("A missing or nonsensical status attribute falls back to 500.")
  void missingStatusFallsBackTo500() throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();

    final JsonNode body = render(errorDispatch(null, "/api/test"), response);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(body.path("type").asText()).isEqualTo(BASE + "internal-error");
  }

  @Test
  @DisplayName("Without the original-path attribute, instance falls back to the dispatch path.")
  void withoutTheOriginalPathInstanceIsTheDispatchPath() throws Exception {
    final JsonNode body = render(errorDispatch(500, null), new MockHttpServletResponse());

    assertThat(body.path("instance").asText()).isEqualTo("/error");
  }
}
