package com.openelements.spring.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Unit tests for {@link JsonAuthenticationEntryPoint} — the entry point that turns Spring
 * Security {@link org.springframework.security.core.AuthenticationException}s into the uniform
 * {@code 401 Unauthorized} JSON body used by both filter chains (JWT and external API-key).
 *
 * <h2>What is tested</h2>
 *
 * <p>Two production wirings — Bearer and {@code ApiKey realm="external"} — and the constructor
 * null-guards. For each wiring the test verifies HTTP status {@code 401}, {@code Content-Type:
 * application/json}, the {@code WWW-Authenticate} header per RFC 7235 §3.1, and the JSON body
 * shape {@code { "status": 401, "error": "Unauthorized", "message": "<reason>" }}.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context, no Mockito. Spring's {@link MockHttpServletRequest} /
 * {@link MockHttpServletResponse} provide the servlet-API plumbing without spinning up a real
 * container. The body is parsed back with Jackson and asserted field-by-field via {@code
 * JsonNode} — the test does not rely on string equality of the JSON output.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. {@link MockHttpServletRequest} and {@link
 * MockHttpServletResponse} are Spring's official servlet stubs, not Mockito mocks; the {@link
 * ObjectMapper} is a real production instance. Entry-point behaviour is leaf-level glue between
 * exception and HTTP response — no collaborator needs substitution.
 */
@DisplayName("JsonAuthenticationEntryPoint")
class JsonAuthenticationEntryPointTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName(
      "Bearer scheme wiring: commence() writes 401 + JSON body + WWW-Authenticate: Bearer, "
          + "preserving the exception's message verbatim.")
  void shouldWriteUniformJsonBodyForBearerScheme() throws Exception {
    final JsonAuthenticationEntryPoint entryPoint =
        new JsonAuthenticationEntryPoint(objectMapper, "Bearer");
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(
        request, response, new BadCredentialsException("Full authentication is required"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    final JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.get("status").asInt()).isEqualTo(401);
    assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
    assertThat(body.get("message").asText()).isEqualTo("Full authentication is required");
  }

  @Test
  @DisplayName(
      "ApiKey realm=\"external\" scheme wiring: same body shape, scheme header changes to ApiKey realm=\"external\".")
  void shouldWriteUniformJsonBodyForApiKeyScheme() throws Exception {
    final JsonAuthenticationEntryPoint entryPoint =
        new JsonAuthenticationEntryPoint(objectMapper, "ApiKey realm=\"external\"");
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .isEqualTo("ApiKey realm=\"external\"");
    final JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.get("message").asText()).isEqualTo("Invalid API key");
  }

  @Test
  @DisplayName("Constructor rejects a null ObjectMapper with NullPointerException.")
  void shouldRejectNullObjectMapper() {
    assertThatThrownBy(() -> new JsonAuthenticationEntryPoint(null, "Bearer"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Constructor rejects a null WWW-Authenticate scheme value with NullPointerException.")
  void shouldRejectNullScheme() {
    assertThatThrownBy(() -> new JsonAuthenticationEntryPoint(objectMapper, null))
        .isInstanceOf(NullPointerException.class);
  }
}
