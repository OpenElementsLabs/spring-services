package com.openelements.spring.base.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openelements.spring.base.security.JsonAuthenticationEntryPoint;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.apikey.ApiKeyEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("ApiKeyAuthenticationFilter")
class ApiKeyAuthenticationFilterTest {

  private final ApiKeyDataService apiKeyService = mock(ApiKeyDataService.class);
  private final JsonAuthenticationEntryPoint entryPoint =
      new JsonAuthenticationEntryPoint(new ObjectMapper(), "ApiKey realm=\"external\"");
  private final ApiKeyAuthenticationFilter filter =
      new ApiKeyAuthenticationFilter(apiKeyService, entryPoint);
  private final FilterChain filterChain = mock(FilterChain.class);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static ApiKeyEntity createValidEntity() {
    final ApiKeyEntity entity = new ApiKeyEntity();
    entity.setId(UUID.randomUUID());
    entity.setName("Test Key");
    entity.setKeyHash("hash");
    entity.setKeyPrefix("crm_test...1234");
    entity.setCreatedBy("admin");
    return entity;
  }

  @Nested
  @DisplayName("when no API key header is present")
  class NoApiKeyHeaderTest {

    @Test
    void shouldPassThroughToNextFilter() throws ServletException, IOException {
      // GIVEN
      final MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/api/external/data");
      final MockHttpServletResponse response = new MockHttpServletResponse();

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      verify(filterChain).doFilter(request, response);
      verifyNoInteractions(apiKeyService);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Nested
  @DisplayName("when API key header is present but invalid")
  class InvalidApiKeyTest {

    @Test
    void shouldReturn401WithUniformJsonBody() throws ServletException, IOException {
      // GIVEN
      final MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/api/external/data");
      request.addHeader("X-API-Key", "invalid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("invalid_key")).thenReturn(Optional.empty());

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
      assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
          .isEqualTo("ApiKey realm=\"external\"");
      final JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
      assertThat(body.get("status").asInt()).isEqualTo(401);
      assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
      assertThat(body.get("message").asText()).isEqualTo("Invalid API key");
      verify(filterChain, never()).doFilter(any(), any());
    }
  }

  @Nested
  @DisplayName("when API key is valid")
  class ValidApiKeyTest {

    @Test
    void shouldAuthenticateForGetRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/api/external/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(200);
      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
      assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
      assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
          .isSameAs(entity);
    }

    @Test
    void shouldAuthenticateForHeadRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request =
          new MockHttpServletRequest("HEAD", "/api/external/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(200);
      verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateForOptionsRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request =
          new MockHttpServletRequest("OPTIONS", "/api/external/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(200);
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("authenticates POST request without enforcing method (chain handles 403)")
    void shouldAuthenticatePostRequestWithoutEnforcingMethod()
        throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request =
          new MockHttpServletRequest("POST", "/api/external/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(200);
      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
      assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
      assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
          .isSameAs(entity);
    }

    @Test
    void shouldHaveApiKeyRole() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request =
          new MockHttpServletRequest("GET", "/api/external/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      final var auth = SecurityContextHolder.getContext().getAuthentication();
      assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_API_KEY"));
    }
  }

  @Test
  void shouldRejectNullApiKeyService() {
    assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(null, entryPoint))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullEntryPoint() {
    assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(apiKeyService, null))
        .isInstanceOf(NullPointerException.class);
  }
}
