package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * Unit tests for {@link ProblemAuthenticationEntryPoint} and {@link ProblemAccessDeniedHandler}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Both handlers run in the Spring Security filter chain, before the {@code DispatcherServlet},
 * and write the response themselves. Nothing downstream can correct them, so the tests assert the
 * full response:
 *
 * <ul>
 *   <li>the status and {@code application/problem+json}, so a {@code 401} is the same shape as a
 *       {@code 409} from a controller;
 *   <li>the {@code WWW-Authenticate} header, which RFC 7235 §3.1 makes mandatory on a {@code 401}
 *       and which differs per chain — a client that cannot see which scheme to use cannot retry;
 *   <li>a {@code traceId}, because the trace filter is ordered ahead of Spring Security for exactly
 *       this reason and a missing one here would be invisible until someone tried to correlate a
 *       login failure.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Spring's servlet-API test doubles, parsing the written JSON. No mocks and no
 * Spring context — both handlers take only a factory and a writer.
 */
@DisplayName("Problem security handlers")
class ProblemSecurityHandlersTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProblemFactory problemFactory =
      new DefaultProblemFactory(new ProblemDetailsProperties(true, BASE, true));
  private final ProblemResponseWriter responseWriter = new ProblemResponseWriter(objectMapper);

  private JsonNode bodyOf(final MockHttpServletResponse response) throws Exception {
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    return objectMapper.readTree(response.getContentAsByteArray());
  }

  @Nested
  @DisplayName("ProblemAuthenticationEntryPoint")
  class EntryPoint {

    @Test
    @DisplayName("An invalid API key yields a 401 unauthorized with the ApiKey challenge.")
    void invalidApiKey() throws Exception {
      final var entryPoint =
          new ProblemAuthenticationEntryPoint(
              problemFactory, responseWriter, "ApiKey realm=\"external\"");
      final var request = new MockHttpServletRequest("GET", "/api/external/contacts");
      final var response = new MockHttpServletResponse();

      entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));

      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
          .isEqualTo("ApiKey realm=\"external\"");
      final JsonNode body = bodyOf(response);
      assertThat(body.path("type").asText()).isEqualTo(BASE + "unauthorized");
      assertThat(body.path("title").asText()).isEqualTo("Unauthorized");
      assertThat(body.path("status").asInt()).isEqualTo(401);
      assertThat(body.path("detail").asText()).isEqualTo("Invalid API key");
      assertThat(body.path("instance").asText()).isEqualTo("/api/external/contacts");
      assertThat(body.path("traceId").asText()).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("A missing JWT yields a 401 unauthorized with the Bearer challenge.")
    void missingJwt() throws Exception {
      final var entryPoint =
          new ProblemAuthenticationEntryPoint(problemFactory, responseWriter, "Bearer");
      final var response = new MockHttpServletResponse();

      entryPoint.commence(
          new MockHttpServletRequest("GET", "/api/test/ok"),
          response,
          new InsufficientAuthenticationException(
              "Full authentication is required to access this resource"));

      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
      assertThat(bodyOf(response).path("type").asText()).isEqualTo(BASE + "unauthorized");
    }

    @Test
    @DisplayName("Null constructor arguments are rejected.")
    void nullArgumentsRejected() {
      assertThatThrownBy(
              () -> new ProblemAuthenticationEntryPoint(null, responseWriter, "Bearer"))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new ProblemAuthenticationEntryPoint(problemFactory, null, "Bearer"))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () -> new ProblemAuthenticationEntryPoint(problemFactory, responseWriter, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ProblemAccessDeniedHandler")
  class AccessDenied {

    @Test
    @DisplayName("A deactivated account yields a 403 forbidden carrying the reason.")
    void deactivatedAccount() throws Exception {
      final var handler = new ProblemAccessDeniedHandler(problemFactory, responseWriter);
      final var response = new MockHttpServletResponse();

      handler.handle(
          new MockHttpServletRequest("GET", "/api/contacts"),
          response,
          new AccessDeniedException("User account is disabled"));

      assertThat(response.getStatus()).isEqualTo(403);
      final JsonNode body = bodyOf(response);
      assertThat(body.path("type").asText()).isEqualTo(BASE + "forbidden");
      assertThat(body.path("title").asText()).isEqualTo("Forbidden");
      assertThat(body.path("status").asInt()).isEqualTo(403);
      assertThat(body.path("detail").asText()).isEqualTo("User account is disabled");
      assertThat(body.path("traceId").asText()).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("No WWW-Authenticate header is set on a 403 — the caller is already authenticated.")
    void noChallengeOnForbidden() throws Exception {
      final var handler = new ProblemAccessDeniedHandler(problemFactory, responseWriter);
      final var response = new MockHttpServletResponse();

      handler.handle(
          new MockHttpServletRequest("POST", "/api/external/contacts"),
          response,
          new AccessDeniedException("Access Denied"));

      assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    @DisplayName("Null constructor arguments are rejected.")
    void nullArgumentsRejected() {
      assertThatThrownBy(() -> new ProblemAccessDeniedHandler(null, responseWriter))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new ProblemAccessDeniedHandler(problemFactory, null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
