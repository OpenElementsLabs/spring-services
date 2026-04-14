package com.openelements.spring.base.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("ApiKeyAuthenticationFilter")
class ApiKeyAuthenticationFilterTest {

  private final ApiKeyDataService apiKeyService = mock(ApiKeyDataService.class);
  private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService);
  private final FilterChain filterChain = mock(FilterChain.class);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("when no API key header is present")
  class NoApiKeyHeaderTest {

    @Test
    void shouldPassThroughToNextFilter() throws ServletException, IOException {
      // GIVEN
      final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
      final MockHttpServletResponse response = new MockHttpServletResponse();

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      verify(filterChain).doFilter(request, response);
      verifyNoInteractions(apiKeyService);
    }
  }

  @Nested
  @DisplayName("when API key header is present but invalid")
  class InvalidApiKeyTest {

    @Test
    void shouldReturn401() throws ServletException, IOException {
      // GIVEN
      final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
      request.addHeader("X-API-Key", "invalid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("invalid_key")).thenReturn(Optional.empty());

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getContentAsString()).contains("Invalid API key");
      verify(filterChain, never()).doFilter(any(), any());
    }
  }

  @Nested
  @DisplayName("when API key is valid")
  class ValidApiKeyTest {

    private ApiKeyEntity createValidEntity() {
      final ApiKeyEntity entity = new ApiKeyEntity();
      entity.setId(UUID.randomUUID());
      entity.setName("Test Key");
      entity.setKeyHash("hash");
      entity.setKeyPrefix("crm_test...1234");
      entity.setCreatedBy("admin");
      return entity;
    }

    @Test
    void shouldAuthenticateForGetRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
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
      final MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/api/data");
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
      final MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/data");
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
    void shouldReturn403ForPostRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getContentAsString()).contains("read-only");
      verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn403ForPutRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/data");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(403);
      verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn403ForDeleteRequest() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/data/1");
      request.addHeader("X-API-Key", "crm_valid_key");
      final MockHttpServletResponse response = new MockHttpServletResponse();
      when(apiKeyService.authenticate("crm_valid_key")).thenReturn(Optional.of(entity));

      // WHEN
      filter.doFilterInternal(request, response, filterChain);

      // THEN
      assertThat(response.getStatus()).isEqualTo(403);
      verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldHaveApiKeyRole() throws ServletException, IOException {
      // GIVEN
      final ApiKeyEntity entity = createValidEntity();
      final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
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
    assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(null))
        .isInstanceOf(NullPointerException.class);
  }
}
