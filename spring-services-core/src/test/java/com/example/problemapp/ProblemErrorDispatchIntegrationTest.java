package com.example.problemapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.app.StarterTestApp;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Errors that never reach Spring MVC, through a real servlet container.
 *
 * <h2>What is tested</h2>
 *
 * <p>The container's {@code ERROR} dispatch — the one error path {@code MockMvc} does not perform at
 * all, and therefore the one that no other test in this repository can reach:
 *
 * <ul>
 *   <li><b>A {@code sendError(...)} from a filter becomes a problem of that status</b>, and in
 *       particular is <em>not</em> turned into a {@code 401} by the default chain's {@code
 *       anyRequest().authenticated()}. That is what permitting {@link
 *       jakarta.servlet.DispatcherType#ERROR} buys, and getting it wrong would silently convert
 *       every rate-limit and container error into an authentication failure.
 *   <li><b>{@code instance} names the path the client asked for</b>, not {@code /error}. The
 *       dispatch happens on {@code /error}, so without reading {@code
 *       jakarta.servlet.error.request_uri} every container error would claim the same location.
 *   <li><b>A chain that denies with {@code denyAll} and no access-denied handler</b> — the shape of
 *       the ALTCHA module's challenge chain — still produces a {@code 403} problem, because Spring
 *       Security's default handler calls {@code sendError(403)} and the dispatch lands on the
 *       library's error controller.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>A real Tomcat on a random port, with the library's production filter chains, driven through
 * {@link TestRestTemplate}. The database comes from the shared Postgres Testcontainer because {@link
 * StarterTestApp} is a JPA application.
 *
 * <p><b>Mock-Audit.</b> No mocks. The rate-limit filter and the extra security chain are real beans
 * registered for the test.
 */
@SpringBootTest(classes = StarterTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, ProblemErrorDispatchIntegrationTest.ErrorDispatchBeans.class})
@Testcontainers
@ActiveProfiles("testcontainers")
@AutoConfigureTestRestTemplate
class ProblemErrorDispatchIntegrationTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private static final ObjectMapper MAPPER = new JsonMapper();

  @Autowired private TestRestTemplate restTemplate;

  private JsonNode problem(final ResponseEntity<String> response) throws Exception {
    assertThat(response.getHeaders().getContentType())
        .as("every error response is a problem")
        .hasToString("application/problem+json");
    final JsonNode body = MAPPER.readTree(response.getBody());
    assertThat(body.path("status").asInt()).isEqualTo(response.getStatusCode().value());
    assertThat(body.path("traceId").asString()).matches("[0-9a-f]{32}");
    return body;
  }

  @Test
  @DisplayName("[container] sendError from a filter becomes a problem, not a 401")
  void sendErrorFromAFilterBecomesAProblem() throws Exception {
    final ResponseEntity<String> response =
        restTemplate.getForEntity("/api/test/limited", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    final JsonNode body = problem(response);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "too-many-requests");
    assertThat(body.path("title").asString()).isEqualTo("Too Many Requests");
    assertThat(body.has("detail")).as("the container's message is not curated").isFalse();
  }

  @Test
  @DisplayName("[container] instance on an error dispatch is the original path, not /error")
  void instanceOnAnErrorDispatchIsTheOriginalPath() throws Exception {
    final JsonNode body =
        problem(restTemplate.getForEntity("/api/test/limited", String.class));

    assertThat(body.path("instance").asString()).isEqualTo("/api/test/limited");
  }

  /**
   * The ALTCHA module's challenge chain has exactly this shape: a {@code securityMatcher}, one
   * {@code permitAll} rule and {@code denyAll} for everything else, with no access-denied handler of
   * its own. Spring Security's default handler then calls {@code sendError(403)}, and only the error
   * dispatch can turn that into a problem.
   */
  @Test
  @DisplayName("[container] A denyAll chain without its own handler still yields a 403 problem")
  void denyAllWithoutAHandlerYieldsAProblem() throws Exception {
    final ResponseEntity<String> response =
        restTemplate.postForEntity("/api/public-form/challenge", null, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    final JsonNode body = problem(response);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "forbidden");
    assertThat(body.path("instance").asString()).isEqualTo("/api/public-form/challenge");
  }

  @Test
  @DisplayName("[container] The permitted method on that chain is reachable")
  void permittedMethodOnThatChainIsReachable() {
    assertThat(
            restTemplate.getForEntity("/api/public-form/challenge", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  /** The rate-limit filter and the {@code denyAll} chain the scenarios need. */
  @TestConfiguration(proxyBeanMethods = false)
  static class ErrorDispatchBeans {

    /**
     * Rejects one path with {@code sendError(429)}, the way a rate limiter would. Ordered ahead of
     * Spring Security so the request never reaches a chain — which is exactly the situation in which
     * the default chain used to answer {@code 401} instead.
     */
    @Bean
    FilterRegistrationBean<Filter> rateLimitFilter() {
      final FilterRegistrationBean<Filter> registration =
          new FilterRegistrationBean<>(new RateLimitFilter());
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      return registration;
    }

    /** A stand-in for the ALTCHA module's challenge chain. */
    @Bean
    @Order(0)
    SecurityFilterChain publicFormChain(final HttpSecurity http) throws Exception {
      http.securityMatcher("/api/public-form/**")
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/public-form/**")
                      .permitAll()
                      .anyRequest()
                      .denyAll())
          .csrf(csrf -> csrf.disable());
      return http.build();
    }

    @Bean
    PublicFormController publicFormController() {
      return new PublicFormController();
    }
  }

  /** Answers the one path the {@code denyAll} chain permits. */
  @org.springframework.web.bind.annotation.RestController
  static class PublicFormController {

    @org.springframework.web.bind.annotation.GetMapping(
        value = "/api/public-form/challenge",
        produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    String challenge() {
      return "challenge";
    }
  }

  /** Rejects {@code /api/test/limited} the way a rate limiter would. */
  static class RateLimitFilter implements Filter {

    @Override
    public void doFilter(
        final ServletRequest request, final ServletResponse response, final FilterChain chain)
        throws IOException, ServletException {
      final HttpServletRequest httpRequest = (HttpServletRequest) request;
      if ("/api/test/limited".equals(httpRequest.getRequestURI())) {
        ((HttpServletResponse) response).sendError(HttpStatus.TOO_MANY_REQUESTS.value());
        return;
      }
      chain.doFilter(request, response);
    }
  }
}
