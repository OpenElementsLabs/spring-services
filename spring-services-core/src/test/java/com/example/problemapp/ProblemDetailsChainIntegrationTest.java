package com.example.problemapp;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.app.StarterTestApp;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.apikey.ApiKeyCreateDto;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Problem details through the library's <em>real</em> security filter chains.
 *
 * <h2>What is tested</h2>
 *
 * <p>The error responses that are written outside Spring MVC and that {@code ProblemDetailsWebTest}
 * therefore cannot reach:
 *
 * <ul>
 *   <li>a {@code 401} on the default (JWT) chain and on the external (API-key) chain, each with the
 *       {@code WWW-Authenticate} header its scheme requires;
 *   <li>a {@code 403} from the external chain's {@code denyAll} and from method security;
 *   <li>the trace ID on a {@code 401} — proving that the trace filter really is ordered ahead of
 *       Spring Security, which is a property of the {@code FilterRegistrationBean}'s order and of
 *       nothing a unit test can see.
 * </ul>
 *
 * <p>These are exactly the paths a unit test would get wrong without noticing: an entry point that
 * silently fell back to Spring's default, or an access-denied handler that was never wired into a
 * chain, both produce a plausible status code with the wrong body.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Boots {@link StarterTestApp} with the library's production chains, following {@code
 * CallerRoleLookupChainIntegrationTest}. {@link TestSecurityBeans} contributes a {@code @Primary}
 * {@link JwtDecoder} that decodes one fixed token (the profile's stub decoder throws on use) and a
 * controller with one {@code @PreAuthorize} endpoint.
 *
 * <p><b>Mock-Audit.</b> No behaviour mocks. The API key is created through the real {@code
 * ApiKeyDataService} so the external chain exercises the production hash-and-lookup path.
 */
@SpringBootTest(classes = StarterTestApp.class)
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, ProblemDetailsChainIntegrationTest.TestSecurityBeans.class})
@Testcontainers
@ActiveProfiles("testcontainers")
class ProblemDetailsChainIntegrationTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private static final String PROBLEM_JSON = "application/problem+json";

  private static final String TOKEN = "chain-token";

  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Autowired private MockMvc mockMvc;

  @Autowired private ApiKeyDataService apiKeyDataService;

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("[chain] A request without a JWT is a 401 problem with the Bearer challenge")
  void missingJwtIsAProblem() throws Exception {
    mockMvc
        .perform(get("/api/problem/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(PROBLEM_JSON))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.type").value(BASE + "unauthorized"))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/api/problem/protected"))
        .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")))
        .andExpect(jsonPath("$.error").doesNotExist())
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("[chain] An invalid API key is a 401 problem with the ApiKey challenge")
  void invalidApiKeyIsAProblem() throws Exception {
    mockMvc
        .perform(get("/api/external/problem").header("X-API-Key", "not-a-valid-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(PROBLEM_JSON))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"external\""))
        .andExpect(jsonPath("$.type").value(BASE + "unauthorized"))
        .andExpect(jsonPath("$.detail").value("Invalid API key"))
        .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
  }

  /**
   * The external chain denies every write method declaratively. Before this feature the response was
   * Spring Security's empty {@code 403}; now it carries a problem written by {@code
   * ProblemAccessDeniedHandler}.
   */
  @Test
  @DisplayName("[chain] A denied write on the external chain is a 403 problem")
  void deniedWriteOnTheExternalChainIsAProblem() throws Exception {
    final String rawKey = createApiKey();

    mockMvc
        .perform(post("/api/external/problem").header("X-API-Key", rawKey))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + "forbidden"))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
  }

  @Test
  @DisplayName("[chain] A @PreAuthorize that does not match is a 403 problem")
  void methodSecurityDenialIsAProblem() throws Exception {
    mockMvc
        .perform(get("/api/problem/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + "forbidden"))
        .andExpect(jsonPath("$.status").value(403));
    }

  /**
   * The deactivation gate is the one {@code 403} that originates in the library's own service layer
   * rather than in a Spring Security rule: {@code UserService.getCurrentUserEntity()} throws {@code
   * AccessDeniedException} for a row with {@code active = false}. It reaches the client only because
   * the exception is left to propagate to {@code ExceptionTranslationFilter} instead of being mapped
   * in an {@code @ExceptionHandler} — so this is also the end-to-end proof of that decision.
   */
  @Test
  @DisplayName("[chain] A deactivated account is a 403 problem naming the reason")
  void deactivatedAccountIsAProblem() throws Exception {
    // The first call provisions the user from the JWT; it is active and therefore succeeds.
    mockMvc
        .perform(get("/api/problem/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andExpect(status().isOk());

    final UserEntity user = userRepository.findBySub("chain-user").orElseThrow();
    user.setActive(false);
    userRepository.saveAndFlush(user);

    try {
      mockMvc
          .perform(get("/api/problem/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
          .andExpect(status().isForbidden())
          .andExpect(content().contentType(PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(BASE + "forbidden"))
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.detail").value("User account is disabled"))
          .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    } finally {
      user.setActive(true);
      userRepository.saveAndFlush(user);
    }
  }

  @Test
  @DisplayName("[chain] An application exception behind the JWT chain is a problem")
  void applicationExceptionBehindTheChainIsAProblem() throws Exception {
    mockMvc
        .perform(get("/api/problem/boom").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + "internal-error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
  }

  /**
   * The trace filter is registered with an order below {@code
   * SecurityFilterProperties.DEFAULT_FILTER_ORDER} so that it has already run when an entry point writes a
   * {@code 401}. Nothing but a request through the real chain can prove that.
   */
  @Test
  @DisplayName("[chain] A 401 carries the trace ID of the incoming traceparent")
  void traceIdIsAvailableForUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/problem/protected").header("traceparent", TRACEPARENT))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  @Test
  @DisplayName("[chain] A 403 carries the trace ID of the incoming traceparent")
  void traceIdIsAvailableForForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/problem/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .header("traceparent", TRACEPARENT))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  /**
   * Creates a real API key through {@link ApiKeyDataService#create(ApiKeyCreateDto)} so the external
   * chain exercises the production hash-and-lookup path. {@code create(...)} resolves the current
   * user from a JWT, so a JWT authentication is bound to the context for the duration of the call
   * and cleared afterwards.
   */
  private String createApiKey() {
    final Jwt jwt =
        Jwt.withTokenValue("provisioning-token")
            .header("alg", "none")
            .subject("problem-key-creator")
            .claim("name", "Problem Key Creator")
            .claim("preferred_username", "problem-key-creator")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    try {
      return apiKeyDataService.create(new ApiKeyCreateDto("spec-024 chain test key")).key();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /**
   * Test-scoped beans: a {@code @Primary} {@link JwtDecoder} that decodes one fixture token into a
   * {@link Jwt} without any role (the profile's stub decoder throws on use), and a controller with
   * one endpoint per chain scenario.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class TestSecurityBeans {

    @Bean
    @Primary
    JwtDecoder problemTestJwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject("chain-user")
              .claim("name", "Chain User")
              .claim("preferred_username", "chain-user")
              .issuedAt(Instant.EPOCH)
              .expiresAt(Instant.EPOCH.plusSeconds(3600))
              .build();
    }

    @Bean
    ChainTestController chainTestController(
        final AuthService authService, final UserService userService) {
      return new ChainTestController(authService, userService);
    }
  }

  /** One endpoint per chain scenario. */
  @RestController
  static class ChainTestController {

    private final AuthService authService;

    private final UserService userService;

    ChainTestController(final AuthService authService, final UserService userService) {
      this.authService = authService;
      this.userService = userService;
    }

    @GetMapping(value = "/api/problem/protected", produces = MediaType.TEXT_PLAIN_VALUE)
    String protectedEndpoint() {
      return authService.getRoles().toString();
    }

    @GetMapping(value = "/api/problem/admin", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    String adminEndpoint() {
      return "admin";
    }

    @GetMapping(value = "/api/problem/boom", produces = MediaType.TEXT_PLAIN_VALUE)
    String boom() {
      throw new IllegalStateException("SELECT * FROM users WHERE email='jane@example.com'");
    }

    @GetMapping(value = "/api/external/problem", produces = MediaType.TEXT_PLAIN_VALUE)
    String externalEndpoint() {
      return "ok";
    }

    /** Resolves the current user, which is where the deactivation gate fires. */
    @GetMapping(value = "/api/problem/me", produces = MediaType.TEXT_PLAIN_VALUE)
    String me() {
      return userService.getCurrentUserEntity().getUserName();
    }
  }
}
