package com.example.problemapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.problem.ProblemFactory;
import com.openelements.spring.base.problem.StandardProblemType;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.WebApplicationContext;

/**
 * The problem-details feature over HTTP.
 *
 * <h2>What is tested</h2>
 *
 * <p>Everything a client can observe about an error that goes through Spring MVC: the content type,
 * the members of the body, the {@code type} URI for each kind of failure, what validation produces,
 * and — just as important — what never appears. The security chains are not involved here; they are
 * covered by {@code ProblemDetailsChainIntegrationTest}, which needs the real filter chains and a
 * database.
 *
 * <p>Several of these assertions exist because the failure mode is silent:
 *
 * <ul>
 *   <li>a leaked exception message or rejected value is a perfectly valid-looking response;
 *   <li>a {@code type} URI that drifts breaks client branching without changing a status code;
 *   <li>an application {@code @ExceptionHandler} that the library shadows looks like an application
 *       bug, not a library one.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>A {@link WebApplicationContextRunner} with the real auto-configurations, driven through {@link
 * MockMvc}. No database and no Testcontainers: nothing here needs persistence, and a web slice keeps
 * the feedback loop short enough that every scenario can have its own case.
 *
 * <p><b>Mock-Audit.</b> No mocks. The controllers, the {@code @Validated} service and the
 * application advices are real beans; the only test doubles are Spring's own MockMvc infrastructure.
 */
@DisplayName("Problem details over HTTP")
class ProblemDetailsWebTest {

  /** The default {@code openelements.problem-details.type-base}. */
  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private static final String PROBLEM_JSON = "application/problem+json";

  private static final ObjectMapper MAPPER = new JsonMapper();

  private final WebApplicationContextRunner runner = runner();

  private static WebApplicationContextRunner runner() {
    return new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ProblemDetailsAutoConfiguration.class,
                TraceAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class,
                ValidationAutoConfiguration.class,
                WebMvcAutoConfiguration.class,
                ErrorMvcAutoConfiguration.class))
        .withUserConfiguration(ProblemTestApp.class);
  }

  private void run(final MvcAssertion assertion) {
    run(runner, assertion);
  }

  private void run(final WebApplicationContextRunner custom, final MvcAssertion assertion) {
    custom.run(
        context -> {
          assertion.accept(mockMvc((WebApplicationContext) context));
        });
  }

  /**
   * Builds the {@link MockMvc} with the library's trace filter in front of the servlet.
   *
   * <p>MockMvc runs only the filters it is given; a {@code FilterRegistrationBean} in the context is
   * invisible to it. Without this the trace ID would still reach the response body — {@code
   * TraceContext} resolves one on first read — but the MDC would never be populated, and the
   * assertions about log correlation would pass against a set-up that does not resemble production.
   */
  private static MockMvc mockMvc(final WebApplicationContext context) {
    final jakarta.servlet.Filter traceFilter =
        context
            .getBean("traceContextFilterRegistration", FilterRegistrationBean.class)
            .getFilter();
    return MockMvcBuilders.webAppContextSetup(context).addFilters(traceFilter).build();
  }

  /** Performs a request and returns the parsed problem, asserting the content type on the way. */
  private static JsonNode problem(final MockMvc mvc, final org.springframework.test.web.servlet.RequestBuilder request)
      throws Exception {
    final var result =
        mvc.perform(request).andExpect(content().contentType(PROBLEM_JSON)).andReturn();
    final JsonNode body = MAPPER.readTree(result.getResponse().getContentAsByteArray());
    assertThat(body.path("status").asInt())
        .as("the body's status must agree with the status line")
        .isEqualTo(result.getResponse().getStatus());
    assertThat(body.path("traceId").asString()).matches("[0-9a-f]{32}");
    assertThat(body.path("type").asString()).startsWith(BASE);
    assertThat(body.path("title").asString()).isNotBlank();
    return body;
  }

  @FunctionalInterface
  private interface MvcAssertion {
    void accept(MockMvc mvc) throws Exception;
  }

  // ------------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("Common problem shape")
  class CommonShape {

    @Test
    @DisplayName("Every problem carries type, title, status and a non-zero traceId")
    void mandatoryMembers() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/fail"));
            assertThat(body.path("type").asString()).isEqualTo(BASE + "not-found");
            assertThat(body.path("title").asString()).isEqualTo("Not Found");
            assertThat(body.path("status").asInt()).isEqualTo(404);
            assertThat(body.path("traceId").asString()).isNotEqualTo("0".repeat(32));
          });
    }

    @Test
    @DisplayName("instance is the path without the query string")
    void instanceOmitsTheQueryString() {
      run(
          mvc -> {
            final JsonNode body =
                problem(mvc, get("/api/test/fail").param("email", "jane@example.com"));
            assertThat(body.path("instance").asString()).isEqualTo("/api/test/fail");
            assertThat(body.toString()).doesNotContain("jane@example.com");
          });
    }

    @Test
    @DisplayName("include-instance=false removes the member")
    void instanceCanBeSwitchedOff() {
      run(
          runner.withPropertyValues("openelements.problem-details.include-instance=false"),
          mvc -> assertThat(problem(mvc, get("/api/test/fail")).has("instance")).isFalse());
    }

    @Test
    @DisplayName("Accept: text/html still receives a problem, not an HTML page")
    void problemsAreJsonEvenForBrowsers() {
      run(
          mvc ->
              mvc.perform(get("/api/test/fail").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                  .andExpect(status().isNotFound())
                  .andExpect(content().contentType(PROBLEM_JSON))
                  .andExpect(jsonPath("$.type").value(BASE + "not-found")));
    }
  }

  @Nested
  @DisplayName("Type URIs")
  class TypeUris {

    @Test
    @DisplayName("A configured base applies to the library's types and the application's alike")
    void configuredBaseAppliesToAllTypes() {
      final String govStack = "https://govstack.global/problems/registries/";
      run(
          runner.withPropertyValues("openelements.problem-details.type-base=" + govStack),
          mvc -> {
            mvc.perform(get("/api/test/fail"))
                .andExpect(jsonPath("$.type").value(govStack + "not-found"));
            mvc.perform(get("/api/test/problem-enum"))
                .andExpect(jsonPath("$.type").value(govStack + "duplicate-contact"));
          });
    }

    @Test
    @DisplayName("A status without a standard type becomes http-<code>")
    void statusWithoutAStandardType() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/status/418"));
            assertThat(body.path("status").asInt()).isEqualTo(418);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "http-418");
            assertThat(body.path("title").asString()).isEqualTo("HTTP 418");
          });
    }

    @Test
    @DisplayName("Status types are frozen: 413 is content-too-large, not Spring's constant name")
    void statusTypesAreFrozen() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/status/413"));
            assertThat(body.path("type").asString()).isEqualTo(BASE + "content-too-large");
            assertThat(body.path("title").asString()).isEqualTo("Content Too Large");
          });
    }
  }

  @Nested
  @DisplayName("ProblemException")
  class ThrownProblems {

    @Test
    @DisplayName("A typed problem needs no handler")
    void typedProblemWithoutAHandler() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/problem"));
            assertThat(body.path("status").asInt()).isEqualTo(409);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "duplicate-record");
            assertThat(body.path("title").asString()).isEqualTo("Duplicate record");
            assertThat(body.path("detail").asString()).isEqualTo("Record already exists");
          });
    }

    @Test
    @DisplayName("Without a detail the member is absent")
    void problemWithoutDetail() {
      run(mvc -> assertThat(problem(mvc, get("/api/test/problem-without-detail")).has("detail")).isFalse());
    }

    @Test
    @DisplayName("Extension members sit next to the standard members")
    void extensionMembers() {
      run(
          mvc ->
              assertThat(problem(mvc, get("/api/test/problem-with-extension")).path("existingId").asString())
                  .isEqualTo("42"));
    }

    @Test
    @DisplayName("A subclass of ProblemException is rendered by its type")
    void subclassOfProblemException() {
      run(
          mvc ->
              assertThat(problem(mvc, get("/api/test/problem-subclass")).path("type").asString())
                  .isEqualTo(BASE + "duplicate-record"));
    }

    @Test
    @DisplayName("An application enum works as a problem type")
    void applicationEnumAsProblemType() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/problem-enum"));
            assertThat(body.path("status").asInt()).isEqualTo(409);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "duplicate-contact");
          });
    }

    /**
     * A {@code 503} is an operational event. If it were logged at {@code DEBUG} like a client error,
     * an outage would leave no trace in a production log — and the trace ID in the MDC is what ties
     * the client's report to that entry.
     */
    @Test
    @DisplayName("A 5xx problem is logged at ERROR with the response's traceId in the MDC")
    void serverErrorProblemsAreLogged() {
      final Logger resolverLog =
          (Logger)
              LoggerFactory.getLogger(
                  "com.openelements.spring.base.problem.ProblemHandlerExceptionResolver");
      final ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      resolverLog.addAppender(appender);
      try {
        run(
            mvc -> {
              final JsonNode body = problem(mvc, get("/api/test/problem-5xx"));
              assertThat(body.path("status").asInt()).isEqualTo(503);
              assertThat(body.path("type").asString()).isEqualTo(BASE + "upstream-down");

              assertThat(appender.list)
                  .anySatisfy(
                      event -> {
                        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                        assertThat(event.getMDCPropertyMap())
                            .containsEntry("traceId", body.path("traceId").asString());
                      });
            });
      } finally {
        resolverLog.detachAppender(appender);
      }
    }
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    private static final String BROKEN_ORDER =
        """
        {"address": {"street": "", "postal_code": "ABC"},
         "items": [{"name": "much too long"}],
         "labels": {"a/b~c": ""}}
        """;

    @Test
    @DisplayName("Body errors carry JSON pointers in the names the client sent")
    void bodyErrorsCarryPointers() {
      run(
          mvc -> {
            final JsonNode body =
                problem(
                    mvc,
                    post("/api/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BROKEN_ORDER));

            assertThat(body.path("status").asInt()).isEqualTo(400);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "validation-failed");
            assertThat(body.path("title").asString()).isEqualTo("Validation failed");

            final var pointers = body.path("errors").findValuesAsString("pointer");
            assertThat(pointers)
                .contains("/address/street", "/address/postal_code", "/items/0/name", "/labels/a~1b~0c");
            assertThat(body.path("errors")).allSatisfy(entry ->
                assertThat(entry.path("message").asString()).isNotBlank());
          });
    }

    @Test
    @DisplayName("No rejected value appears anywhere in the body")
    void rejectedValuesAreNeverEchoed() {
      run(
          mvc -> {
            final JsonNode body =
                problem(
                    mvc,
                    post("/api/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BROKEN_ORDER));

            assertThat(body.toString()).doesNotContain("much too long").doesNotContain("\"ABC\"");
          });
    }

    @Test
    @DisplayName("A naming strategy renames every pointer segment")
    void namingStrategyAppliesToPointers() {
      run(
          runner.withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE"),
          mvc -> {
            final JsonNode body =
                problem(
                    mvc,
                    post("/api/test/body-and-param")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"));

            assertThat(body.path("errors").findValuesAsString("pointer")).contains("/name");
          });
    }

    @Test
    @DisplayName("A class-level constraint on the body root yields the empty pointer")
    void classLevelConstraintYieldsEmptyPointer() {
      run(
          mvc -> {
            final JsonNode body =
                problem(
                    mvc,
                    post("/api/test/range")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\": 2, \"to\": 1}"));

            assertThat(body.path("errors").findValuesAsString("pointer")).containsExactly("");
          });
    }

    @Test
    @DisplayName("A query parameter failure is invalid-parameter with no errors array")
    void queryParameterOnly() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/param").param("size", "5000"));

            assertThat(body.path("status").asInt()).isEqualTo(400);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "invalid-parameter");
            assertThat(body.has("errors")).isFalse();
            assertThat(body.path("detail").asString())
                .isEqualTo("Invalid query parameter 'size': must be less than or equal to 100.");
          });
    }

    @Test
    @DisplayName("Path and header failures are named by their location")
    void pathAndHeaderParameters() {
      run(
          mvc -> {
            final JsonNode body =
                problem(mvc, get("/api/test/path-and-header/0").header("X-Page", "0"));

            assertThat(body.path("type").asString()).isEqualTo(BASE + "invalid-parameter");
            assertThat(body.path("detail").asString())
                .contains("path parameter 'id'")
                .contains("header parameter 'X-Page'");
          });
    }

    @Test
    @DisplayName("Body and parameter failures together are validation-failed with both")
    void bodyAndParameterTogether() {
      run(
          mvc -> {
            final JsonNode body =
                problem(
                    mvc,
                    post("/api/test/body-and-param")
                        .param("size", "5000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"));

            assertThat(body.path("type").asString()).isEqualTo(BASE + "validation-failed");
            assertThat(body.path("errors").findValuesAsString("pointer")).containsExactly("/name");
            assertThat(body.path("detail").asString()).contains("query parameter 'size'");
          });
    }

    @Test
    @DisplayName("A type mismatch names the parameter but never the value")
    void typeMismatchDoesNotEchoTheValue() {
      run(
          mvc -> {
            final JsonNode body =
                problem(mvc, get("/api/test/typed").param("size", "jane@example.com"));

            assertThat(body.path("type").asString()).isEqualTo(BASE + "invalid-parameter");
            assertThat(body.path("detail").asString()).contains("'size'");
            assertThat(body.toString()).doesNotContain("jane@example.com");
          });
    }

    @Test
    @DisplayName("A missing required parameter is named")
    void missingRequiredParameter() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/typed"));

            assertThat(body.path("type").asString()).isEqualTo(BASE + "invalid-parameter");
            assertThat(body.path("detail").asString()).contains("'size'");
          });
    }

    @Test
    @DisplayName("A @Validated controller's violation is a 400 at the boundary")
    void validatedControllerIsABoundary() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/validated/param").param("size", "5000"));

            assertThat(body.path("status").asInt()).isEqualTo(400);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "invalid-parameter");
          });
    }

    /**
     * The same exception type from one level deeper means the application built the argument, not
     * the client. Answering {@code 400} there would blame the caller for a server-side defect.
     */
    @Test
    @DisplayName("A violation from a service deeper down is a 500")
    void serviceLayerViolationIsAServerError() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/service-violation"));

            assertThat(body.path("status").asInt()).isEqualTo(500);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "internal-error");
          });
    }

    @Test
    @DisplayName("A malformed body leaks no parser message, class name or content")
    void malformedBody() {
      run(
          mvc -> {
            final JsonNode body =
                problem(
                    mvc,
                    post("/api/test/body-and-param")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "));

            assertThat(body.path("status").asInt()).isEqualTo(400);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "malformed-request");
            assertThat(body.toString())
                .doesNotContain("JsonParseException")
                .doesNotContain("JsonEOFException")
                .doesNotContain("com.fasterxml")
                .doesNotContain("Simple");
          });
    }
  }

  @Nested
  @DisplayName("Standard MVC errors")
  class StandardMvcErrors {

    @Test
    @DisplayName("An unknown path is not-found")
    void unknownPath() {
      run(
          mvc ->
              assertThat(problem(mvc, get("/api/does-not-exist")).path("type").asString())
                  .isEqualTo(BASE + "not-found"));
    }

    @Test
    @DisplayName("A wrong method is method-not-allowed and keeps the Allow header")
    void wrongMethodKeepsAllow() {
      run(
          mvc -> {
            mvc.perform(delete("/api/test/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, org.hamcrest.Matchers.containsString("GET")))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(BASE + "method-not-allowed"));
          });
    }

    @Test
    @DisplayName("A wrong content type is unsupported-media-type")
    void unsupportedMediaType() {
      run(
          mvc ->
              mvc.perform(
                      post("/api/test/body")
                          .contentType(MediaType.TEXT_PLAIN)
                          .content("not json"))
                  .andExpect(status().isUnsupportedMediaType())
                  .andExpect(content().contentType(PROBLEM_JSON))
                  .andExpect(jsonPath("$.type").value(BASE + "unsupported-media-type")));
    }
  }

  @Nested
  @DisplayName("Application exceptions")
  class ApplicationExceptions {

    @Test
    @DisplayName("@ResponseStatus wins over internal-error and contributes its reason")
    void responseStatusWins() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/response-status"));

            assertThat(body.path("status").asInt()).isEqualTo(409);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "conflict");
            assertThat(body.path("detail").asString()).isEqualTo("Already taken");
          });
    }

    @Test
    @DisplayName("A ResponseStatusException's reason becomes the detail")
    void responseStatusExceptionReason() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/not-found-with-reason"));

            assertThat(body.path("type").asString()).isEqualTo(BASE + "not-found");
            assertThat(body.path("detail").asString()).isEqualTo("Contact not found");
          });
    }

    @Test
    @DisplayName("A 502 from an upstream call becomes bad-gateway")
    void upstreamFailureIsBadGateway() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/bad-gateway"));

            assertThat(body.path("status").asInt()).isEqualTo(502);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "bad-gateway");
            assertThat(body.path("detail").asString()).isEqualTo("Translation API call failed");
          });
    }

    @Test
    @DisplayName("An unexpected exception leaks neither its message nor its class name")
    void unexpectedExceptionLeaksNothing() {
      run(
          mvc -> {
            final JsonNode body = problem(mvc, get("/api/test/boom"));

            assertThat(body.path("status").asInt()).isEqualTo(500);
            assertThat(body.path("type").asString()).isEqualTo(BASE + "internal-error");
            assertThat(body.path("detail").asString()).isEqualTo("An unexpected error occurred.");
            assertThat(body.toString())
                .doesNotContain("jane@example.com")
                .doesNotContain("SELECT")
                .doesNotContain("IllegalStateException");
          });
    }

    /**
     * The library's catch-all is a {@code HandlerExceptionResolver} placed after Spring's advice
     * resolver precisely so this works without the application having to think about ordering.
     */
    @Test
    @DisplayName("An application advice without @Order still wins over the catch-all")
    void applicationAdviceWinsWithoutOrder() {
      run(
          runner.withUserConfiguration(UnorderedApplicationAdvice.class),
          mvc ->
              mvc.perform(get("/api/test/boom"))
                  .andExpect(status().isIAmATeapot())
                  .andExpect(content().string("handled by the application")));
    }

    @Test
    @DisplayName("An application advice can build its response with the ProblemFactory")
    void applicationAdviceCanUseTheFactory() {
      run(
          runner.withUserConfiguration(FactoryUsingApplicationAdvice.class),
          mvc ->
              mvc.perform(get("/api/test/boom"))
                  .andExpect(status().isServiceUnavailable())
                  .andExpect(content().contentType(PROBLEM_JSON))
                  .andExpect(jsonPath("$.type").value(BASE + "service-unavailable"))
                  .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}"))));
    }

    @Test
    @DisplayName("An application advice with @Order overrides a standard MVC mapping")
    void applicationAdviceWithOrderOverridesTheLibrary() {
      run(
          runner.withUserConfiguration(OrderedValidationAdvice.class),
          mvc ->
              mvc.perform(
                      post("/api/test/body")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"address\": {\"street\": \"\", \"postal_code\": \"12345\"}}"))
                  .andExpect(status().isIAmATeapot())
                  .andExpect(content().string("validation handled by the application")));
    }

    @Test
    @DisplayName("An application ProblemFactory shapes every problem")
    void applicationFactoryShapesEveryProblem() {
      run(
          runner.withUserConfiguration(ApplicationProblemFactory.class),
          mvc ->
              mvc.perform(get("/api/test/fail"))
                  .andExpect(status().isNotFound())
                  .andExpect(jsonPath("$.tenant").value("acme"))
                  .andExpect(jsonPath("$.traceId").doesNotExist()));
    }
  }

  // --- application-supplied beans -------------------------------------------------------------

  /** An advice with no {@code @Order}, which therefore sits at {@code LOWEST_PRECEDENCE}. */
  @Configuration(proxyBeanMethods = false)
  static class UnorderedApplicationAdvice {

    @Bean
    Advice unorderedAdvice() {
      return new Advice();
    }

    @RestControllerAdvice
    static class Advice {
      @ExceptionHandler(IllegalStateException.class)
      ResponseEntity<String> handle(final IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).body("handled by the application");
      }
    }
  }

  /** An advice that builds an RFC 9457 body of its own through the library's factory. */
  @Configuration(proxyBeanMethods = false)
  static class FactoryUsingApplicationAdvice {

    @Bean
    Advice factoryUsingAdvice(final ProblemFactory problems) {
      return new Advice(problems);
    }

    @RestControllerAdvice
    static class Advice {

      private final ProblemFactory problems;

      Advice(final ProblemFactory problems) {
        this.problems = problems;
      }

      @ExceptionHandler(IllegalStateException.class)
      ProblemDetail handle(final IllegalStateException e, final HttpServletRequest request) {
        return problems.create(StandardProblemType.SERVICE_UNAVAILABLE, null, request);
      }
    }
  }

  /**
   * An advice that takes over a mapping the library's own advice also handles. Both sit at
   * {@code LOWEST_PRECEDENCE} by default, so the application has to declare an {@code @Order} —
   * which is exactly what the documentation tells it to do.
   */
  @Configuration(proxyBeanMethods = false)
  static class OrderedValidationAdvice {

    @Bean
    Advice orderedValidationAdvice() {
      return new Advice();
    }

    @RestControllerAdvice
    @Order(0)
    static class Advice {
      @ExceptionHandler(MethodArgumentNotValidException.class)
      ResponseEntity<String> handle(final MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT)
            .body("validation handled by the application");
      }
    }
  }

  /** An application factory that adds a tenant member and drops the trace ID. */
  @Configuration(proxyBeanMethods = false)
  static class ApplicationProblemFactory {

    @Bean
    ProblemFactory applicationProblemFactory() {
      return new ProblemFactory() {
        @Override
        public ProblemDetail create(
            final com.openelements.spring.base.problem.ProblemType type,
            final String detail,
            final HttpServletRequest request) {
          final ProblemDetail problem = ProblemDetail.forStatus(type.status());
          problem.setProperty("tenant", "acme");
          return problem;
        }

        @Override
        public ProblemDetail create(
            final com.openelements.spring.base.problem.ProblemException exception,
            final HttpServletRequest request) {
          return create(exception.type(), null, request);
        }
      };
    }
  }
}
