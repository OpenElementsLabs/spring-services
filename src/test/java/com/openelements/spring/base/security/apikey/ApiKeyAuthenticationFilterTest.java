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

/**
 * Unit tests for {@link ApiKeyAuthenticationFilter} — the Spring Security filter that
 * authenticates external requests via the {@code X-API-Key} header.
 *
 * <h2>What is tested</h2>
 *
 * <p>Three filter behaviours are verified:
 *
 * <ol>
 *   <li><b>Header-driven activation.</b> Requests without an {@code X-API-Key} header pass
 *       straight to the next filter without touching {@link ApiKeyDataService} and leave the
 *       {@link SecurityContextHolder} untouched — the filter must never block JWT-authenticated
 *       traffic.
 *   <li><b>Invalid key rejection.</b> A presented but unrecognised key produces a uniform JSON
 *       401 body via {@link JsonAuthenticationEntryPoint} with a {@code WWW-Authenticate} header,
 *       and the filter chain is short-circuited before the controller runs.
 *   <li><b>Valid key authentication.</b> A recognised key installs an {@link ApiKeyEntity} as the
 *       principal with the {@code ROLE_API_KEY} authority and lets the request proceed —
 *       regardless of HTTP method (GET, HEAD, OPTIONS, POST). Method-level enforcement is
 *       deliberately a separate concern handled by {@code @PreAuthorize}.
 * </ol>
 *
 * <p>Constructor null-rejection is also pinned: both collaborators must be non-null at
 * construction so a misconfigured Spring context fails fast.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests, no Spring context. {@link MockHttpServletRequest} and
 * {@link MockHttpServletResponse} drive the filter directly via {@code doFilterInternal}; the
 * {@link SecurityContextHolder} is reset in {@code @AfterEach}.
 *
 * <p><b>Mock-Audit.</b> Two mocks:
 *
 * <ul>
 *   <li>{@link ApiKeyDataService} — mocked so we can stub {@code authenticate(...)} to return
 *       {@code Optional.empty()} or {@code Optional.of(entity)} on demand. A real service would
 *       require a {@code JpaRepository}, a datasource, and seeded rows for every permutation;
 *       the filter under test only cares about the return value of {@code authenticate(...)},
 *       so mocking is the right granularity.
 *   <li>{@link FilterChain} — mocked because the filter's job <em>is</em> to call (or not call)
 *       {@code doFilter(...)} on the next link in the chain; the assertions are
 *       {@code verify(filterChain).doFilter(...)} and {@code verify(filterChain, never())...},
 *       which is exactly what a mock is for. A real chain would require wiring a downstream
 *       servlet, which adds no coverage.
 * </ul>
 *
 * <p>The {@link JsonAuthenticationEntryPoint} is constructed for real (no mock) so the actual
 * JSON shape and the {@code WWW-Authenticate} header value are pinned by the test.
 */
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
    @DisplayName("Without an X-API-Key header the filter passes through silently — ApiKeyDataService is never called and the SecurityContext is untouched.")
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

    /**
     * Pins the rejected-key response shape: status 401, content-type {@code application/json},
     * a {@code WWW-Authenticate: ApiKey realm="external"} header, and a JSON body with
     * {@code status}, {@code error}, and {@code message} fields. The filter chain is short-
     * circuited — {@code doFilter} is never invoked. External clients depend on this exact
     * shape; widening it (e.g. plain text) would break their error handlers.
     */
    @Test
    @DisplayName("An unrecognised X-API-Key produces a 401 JSON body with a WWW-Authenticate header and short-circuits the filter chain.")
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
    @DisplayName("A valid X-API-Key on a GET request installs the ApiKeyEntity as the SecurityContext principal and lets the chain proceed.")
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
    @DisplayName("A valid X-API-Key on a HEAD request authenticates and proceeds — HEAD is a read-only method.")
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
    @DisplayName("A valid X-API-Key on an OPTIONS request authenticates and proceeds — CORS preflight is accepted.")
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

    /**
     * The filter authenticates POST requests too — it does not police HTTP methods. Method-level
     * authorization is the chain's job (e.g. {@code @PreAuthorize} or a request matcher) so a
     * 403 for a forbidden write surface comes from later in the chain, not from this filter.
     * Pins the separation of concerns.
     */
    @Test
    @DisplayName("A valid X-API-Key on a POST request authenticates without enforcing the method — chain decides write access.")
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
    @DisplayName("An authenticated API-key request carries the ROLE_API_KEY GrantedAuthority — distinguishable from JWT principals.")
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
  @DisplayName("Constructor rejects a null ApiKeyDataService with NullPointerException — misconfigured context fails fast.")
  void shouldRejectNullApiKeyService() {
    assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(null, entryPoint))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Constructor rejects a null JsonAuthenticationEntryPoint with NullPointerException — misconfigured context fails fast.")
  void shouldRejectNullEntryPoint() {
    assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(apiKeyService, null))
        .isInstanceOf(NullPointerException.class);
  }
}
