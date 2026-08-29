package com.example.rolesapp;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.app.StarterTestApp;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.apikey.ApiKeyCreateDto;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filter-chain acceptance gate for {@link AuthService#getRoles()} (spec 017).
 *
 * <p>Three claims cannot be proven by a unit test with a hand-bound {@code Authentication}, because
 * they assert what the <em>real</em> production filter chains put into the security context:
 *
 * <ol>
 *   <li>an unauthenticated request to a {@code permitAll} path arrives as {@code {ANONYMOUS}},
 *       proving Spring's {@code AnonymousAuthenticationFilter} runs on the default chain;
 *   <li>a JWT {@code roles} claim arrives prefix-mapped and stripped, with the token's {@code
 *       SCOPE_*} authorities excluded from {@code getRoles()};
 *   <li>an {@code X-API-Key} request to the external chain arrives as {@code {API_KEY}}.
 * </ol>
 *
 * <p>A fourth scenario re-asserts the error semantics specs 006/011 established: an unauthenticated
 * request to a protected path is rejected with {@code 401} and a {@code WWW-Authenticate: Bearer}
 * header — the outcome that keeping anonymous authentication enabled preserves.
 *
 * <p>The test boots {@link StarterTestApp} with the library's production filter chains and drives
 * them through {@link MockMvc}. {@link TestSecurityBeans} contributes a {@code @Primary} {@link
 * JwtDecoder} that returns hand-built {@link Jwt}s for two fixed token strings (the profile's stub
 * decoder throws on use), plus a tiny controller that reports {@code authService.getRoles()} on one
 * endpoint per chain.
 */
@SpringBootTest(classes = StarterTestApp.class)
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, CallerRoleLookupChainIntegrationTest.TestSecurityBeans.class})
@Testcontainers
@ActiveProfiles("testcontainers")
class CallerRoleLookupChainIntegrationTest {

  static final String TOKEN_WITH_ROLES = "token-with-roles";
  static final String TOKEN_WITHOUT_ROLES = "token-without-roles";

  @Autowired private MockMvc mockMvc;

  @Autowired private ApiKeyDataService apiKeyDataService;

  @Test
  @DisplayName("[chain] An anonymous request to a permitAll path arrives as ANONYMOUS")
  void anonymousRequestArrivesAsAnonymous() throws Exception {
    mockMvc
        .perform(get("/api/health/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", containsInAnyOrder("ANONYMOUS")));
  }

  @Test
  @DisplayName("[chain] A JWT roles claim arrives prefix-mapped and stripped, without SCOPE_*")
  void jwtRolesArrivePrefixMappedAndStripped() throws Exception {
    mockMvc
        .perform(get("/api/protected/roles").header(HttpHeaders.AUTHORIZATION, bearer(TOKEN_WITH_ROLES)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", containsInAnyOrder("IT-ADMIN", "EMPLOYEE")));
  }

  @Test
  @DisplayName("[chain] A JWT without a roles claim arrives with no roles")
  void jwtWithoutRolesArrivesEmpty() throws Exception {
    mockMvc
        .perform(
            get("/api/protected/roles").header(HttpHeaders.AUTHORIZATION, bearer(TOKEN_WITHOUT_ROLES)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", emptyIterable()));
  }

  @Test
  @DisplayName("[chain] An API-key request to the external chain arrives as API_KEY")
  void apiKeyRequestArrivesAsApiKey() throws Exception {
    final String rawKey = createApiKey();

    mockMvc
        .perform(get("/api/external/roles").header("X-API-Key", rawKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", containsInAnyOrder("API_KEY")));
  }

  @Test
  @DisplayName("[chain] An unauthenticated request to a protected path is rejected with 401 + WWW-Authenticate: Bearer")
  void unauthenticatedProtectedRequestIsRejectedWith401() throws Exception {
    mockMvc
        .perform(get("/api/protected/roles"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  private static String bearer(final String token) {
    return "Bearer " + token;
  }

  /**
   * Creates a real API key through {@link ApiKeyDataService#create(ApiKeyCreateDto)} so the external
   * chain exercises the production hash-and-lookup path. {@code create(...)} resolves the current
   * user from a JWT, so a JWT authentication is bound to the context for the duration of the call and
   * cleared afterwards — this happens on the test thread, independent of the MockMvc filter chain.
   */
  private String createApiKey() {
    final Jwt jwt =
        Jwt.withTokenValue("provisioning-token")
            .header("alg", "none")
            .subject("api-key-creator")
            .claim("name", "API Key Creator")
            .claim("preferred_username", "api-key-creator")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    try {
      return apiKeyDataService.create(new ApiKeyCreateDto("spec-017 chain test key")).key();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /**
   * Test-scoped beans: a {@code @Primary} {@link JwtDecoder} that decodes the two fixture tokens into
   * hand-built {@link Jwt}s (the profile's stub decoder throws on use), and a controller reporting
   * {@code getRoles()} on one endpoint per production chain. Declared as a {@code @TestConfiguration}
   * so the application's component scan never picks it up; it is only active when {@code @Import}ed.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class TestSecurityBeans {

    @Bean
    @Primary
    JwtDecoder rolesTestJwtDecoder() {
      return token -> {
        final Jwt.Builder builder =
            Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("chain-user")
                .claim("scope", "openid")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600));
        if (TOKEN_WITH_ROLES.equals(token)) {
          builder.claim("roles", List.of("IT-ADMIN", "EMPLOYEE"));
        }
        return builder.build();
      };
    }

    @Bean
    RolesReportingController rolesReportingController(final AuthService authService) {
      return new RolesReportingController(authService);
    }
  }

  /** Reports {@code authService.getRoles()} on one endpoint per production filter chain. */
  @RestController
  static class RolesReportingController {

    private final AuthService authService;

    RolesReportingController(final AuthService authService) {
      this.authService = authService;
    }

    @GetMapping(value = "/api/health/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    Set<String> healthRoles() {
      return authService.getRoles();
    }

    @GetMapping(value = "/api/protected/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    Set<String> protectedRoles() {
      return authService.getRoles();
    }

    @GetMapping(value = "/api/external/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    Set<String> externalRoles() {
      return authService.getRoles();
    }
  }
}
