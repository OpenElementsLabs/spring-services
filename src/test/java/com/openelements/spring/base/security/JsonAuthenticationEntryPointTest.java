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

@DisplayName("JsonAuthenticationEntryPoint")
class JsonAuthenticationEntryPointTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
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
  void shouldRejectNullObjectMapper() {
    assertThatThrownBy(() -> new JsonAuthenticationEntryPoint(null, "Bearer"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullScheme() {
    assertThatThrownBy(() -> new JsonAuthenticationEntryPoint(objectMapper, null))
        .isInstanceOf(NullPointerException.class);
  }
}
