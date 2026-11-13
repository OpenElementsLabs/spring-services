package com.example.problemapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import jakarta.servlet.Filter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Every error response, validated against the GovStack common API components.
 *
 * <h2>What is tested</h2>
 *
 * <p>That an application built on this library can implement a GovStack building block without
 * writing an error layer of its own. The GovStack API Design Guide, chapter 11, is the normative
 * source; the vendored {@code govstack-openapi-common.yaml} turns its prose into something a test
 * can execute.
 *
 * <p>Three things are asserted, in order of how badly they would hurt:
 *
 * <ol>
 *   <li><b>The vendored schema is the one it claims to be.</b> The SHA-256 is checked before
 *       anything else. The version string alone does not identify this file: the same {@code
 *       0.1.0-draft} on the repository's {@code main} branch requires {@code code} and {@code
 *       timestamp} members, which contradicts Guide §11.2/§11.4. A silently swapped file would make
 *       every assertion below meaningless while still passing.
 *   <li><b>Every error response validates.</b> Body-validation failures against {@code
 *       ValidationProblem}, everything else against {@code Problem}. The schema pins the {@code
 *       type} URI shape, the {@code status} range and — strictly — the {@code traceId} pattern
 *       {@code ^(?!0{32}$)[\\da-f]{32}$}.
 *   <li><b>No forbidden member is present.</b> The schema permits unknown members, so it cannot
 *       catch a leftover {@code timestamp}, {@code error}, {@code message} or {@code code}. Those
 *       are the members this feature replaced, and a client that still sees one has no reason to
 *       migrate. The check is therefore made explicitly, outside the schema.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>One parameterised test drives every error scenario from a single list, so a scenario added to
 * {@link #scenarios()} cannot quietly skip validation. The type base is set to {@code
 * https://govstack.global/problems/test/}, which is what the schema's {@code type} pattern requires
 * — and which incidentally proves that the library's own types move under an application's base.
 *
 * <p><b>Mock-Audit.</b> No mocks.
 */
@DisplayName("GovStack contract")
class GovStackContractTest {

  private static final String SCHEMA_RESOURCE = "/govstack/govstack-openapi-common.yaml";

  private static final String EXPECTED_SHA256 =
      "05d1bfc89c86a8d64005e343268326b3fb43cd044fe30f662fec0f9f573b0c73";

  private static final String TYPE_BASE = "https://govstack.global/problems/test/";

  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  /** Members this feature replaced. None of them may survive in a problem. */
  private static final Set<String> FORBIDDEN_MEMBERS =
      Set.of("timestamp", "error", "message", "code");

  private static final ObjectMapper JSON = new JsonMapper();

  private static JsonNode schemaDocument;

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ProblemDetailsAutoConfiguration.class,
                  TraceAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  ValidationAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  ErrorMvcAutoConfiguration.class))
          .withUserConfiguration(ProblemTestApp.class)
          .withPropertyValues("openelements.problem-details.type-base=" + TYPE_BASE);

  @BeforeAll
  static void loadSchema() throws Exception {
    final byte[] bytes;
    try (InputStream in = GovStackContractTest.class.getResourceAsStream(SCHEMA_RESOURCE)) {
      assertThat(in).as("the vendored GovStack schema must be on the test classpath").isNotNull();
      bytes = in.readAllBytes();
    }
    final String actual =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    assertThat(actual)
        .as(
            "the vendored GovStack schema must be the pinned revision — see"
                + " src/test/resources/govstack/README.md")
        .isEqualTo(EXPECTED_SHA256);

    schemaDocument = new YAMLMapper().readTree(bytes);
  }

  @Test
  @DisplayName("The vendored schema is the pinned revision")
  void schemaProvenanceIsVerified() {
    // The assertion lives in loadSchema() so that no other test can run against a swapped file;
    // this case exists so the guarantee is visible in the test report.
    assertThat(schemaDocument.at("/components/schemas/Problem").isObject()).isTrue();
    assertThat(schemaDocument.at("/components/schemas/ValidationProblem").isObject()).isTrue();
  }

  /**
   * A negative control. A schema wired up wrongly — the {@code $ref} not resolving, the dialect not
   * applied, the body handed over as a string — validates everything and reports nothing, and every
   * assertion in this class would then pass while proving nothing at all. These three bodies must be
   * rejected, each for a different reason the GovStack schema actually states.
   */
  @Test
  @DisplayName("The schema rejects what it is supposed to reject")
  void schemaRejectsInvalidBodies() throws Exception {
    final String validType = TYPE_BASE + "not-found";

    assertThat(validate("Problem", JSON.readTree("{\"title\":\"x\",\"status\":404,\"traceId\":\"" + TRACE_ID + "\"}")))
        .as("a problem without 'type' must be rejected")
        .isNotEmpty();
    assertThat(
            validate(
                "Problem",
                JSON.readTree(
                    "{\"type\":\"" + validType + "\",\"title\":\"x\",\"status\":404,\"traceId\":\""
                        + "0".repeat(32)
                        + "\"}")))
        .as("an all-zero traceId must be rejected")
        .isNotEmpty();
    assertThat(
            validate(
                "ValidationProblem",
                JSON.readTree(
                    "{\"type\":\"" + validType + "\",\"title\":\"x\",\"status\":400,\"traceId\":\""
                        + TRACE_ID
                        + "\"}")))
        .as("a ValidationProblem without 'errors' must be rejected")
        .isNotEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("scenarios")
  @DisplayName("Every error response validates against the GovStack schema")
  void everyErrorResponseValidates(final Scenario scenario) {
    runner.run(
        context -> {
          final MockMvc mvc = mockMvc((WebApplicationContext) context);
          final var response =
              mvc.perform(scenario.request().get().header("traceparent", TRACEPARENT))
                  .andReturn()
                  .getResponse();

          assertThat(response.getContentType())
              .as("%s must answer application/problem+json", scenario.name())
              .isEqualTo("application/problem+json");

          final JsonNode body = JSON.readTree(response.getContentAsByteArray());
          final String schemaName = scenario.validationProblem() ? "ValidationProblem" : "Problem";

          assertThat(validate(schemaName, body))
              .as("%s must satisfy the GovStack %s schema; body was %s", scenario.name(), schemaName, body)
              .isEmpty();
        });
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("scenarios")
  @DisplayName("No error response carries a member this feature replaced")
  void noErrorResponseCarriesAForbiddenMember(final Scenario scenario) {
    runner.run(
        context -> {
          final MockMvc mvc = mockMvc((WebApplicationContext) context);
          final JsonNode body =
              JSON.readTree(
                  mvc.perform(scenario.request().get().header("traceparent", TRACEPARENT))
                      .andReturn()
                      .getResponse()
                      .getContentAsByteArray());

          FORBIDDEN_MEMBERS.forEach(
              member ->
                  assertThat(body.has(member))
                      .as("%s must not carry '%s'; body was %s", scenario.name(), member, body)
                      .isFalse());
          body.path("errors")
              .forEach(
                  entry ->
                      assertThat(entry.has("code"))
                          .as("%s: an errors[] entry must not carry 'code'", scenario.name())
                          .isFalse());
        });
  }

  /**
   * Guide §11.3: the trace ID of the response is the trace-id of the request's effective
   * {@code traceparent}. Asserted on every scenario, because a writer that forgot to read {@link
   * com.openelements.spring.base.trace.TraceContext} would still emit a syntactically valid ID.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("scenarios")
  @DisplayName("traceId equals the trace-id of the request's traceparent")
  void traceIdFollowsTheIncomingTraceParent(final Scenario scenario) {
    runner.run(
        context -> {
          final MockMvc mvc = mockMvc((WebApplicationContext) context);
          final JsonNode body =
              JSON.readTree(
                  mvc.perform(scenario.request().get().header("traceparent", TRACEPARENT))
                      .andReturn()
                      .getResponse()
                      .getContentAsByteArray());

          assertThat(body.path("traceId").asString()).isEqualTo(TRACE_ID);
        });
  }

  /**
   * One entry per error this library can produce through Spring MVC. Driving all three test methods
   * from this one list is deliberate: a scenario added here is validated, checked for forbidden
   * members and checked for its trace ID, with no way to add one and validate only part of it.
   */
  static List<Scenario> scenarios() {
    return List.of(
        new Scenario("not-found", () -> get("/api/test/fail"), false),
        new Scenario("unknown-path", () -> get("/api/does-not-exist"), false),
        new Scenario("method-not-allowed", () -> delete("/api/test/only-get"), false),
        new Scenario(
            "unsupported-media-type",
            () -> post("/api/test/body").contentType(MediaType.TEXT_PLAIN).content("x"),
            false),
        new Scenario("conflict-response-status", () -> get("/api/test/response-status"), false),
        new Scenario("not-found-with-reason", () -> get("/api/test/not-found-with-reason"), false),
        new Scenario("bad-gateway", () -> get("/api/test/bad-gateway"), false),
        new Scenario("internal-error", () -> get("/api/test/boom"), false),
        new Scenario("problem-exception", () -> get("/api/test/problem"), false),
        new Scenario("problem-without-detail", () -> get("/api/test/problem-without-detail"), false),
        new Scenario("problem-with-extension", () -> get("/api/test/problem-with-extension"), false),
        new Scenario("problem-enum", () -> get("/api/test/problem-enum"), false),
        new Scenario("problem-5xx", () -> get("/api/test/problem-5xx"), false),
        new Scenario("http-418", () -> get("/api/test/status/418"), false),
        new Scenario("invalid-parameter", () -> get("/api/test/param").param("size", "5000"), false),
        new Scenario(
            "invalid-parameter-path-and-header",
            () -> get("/api/test/path-and-header/0").header("X-Page", "0"),
            false),
        new Scenario(
            "invalid-parameter-type-mismatch",
            () -> get("/api/test/typed").param("size", "not-a-number"),
            false),
        new Scenario("invalid-parameter-missing", () -> get("/api/test/typed"), false),
        new Scenario(
            "invalid-parameter-validated-controller",
            () -> get("/api/validated/param").param("size", "5000"),
            false),
        new Scenario("service-violation", () -> get("/api/test/service-violation"), false),
        new Scenario(
            "malformed-request",
            () ->
                post("/api/test/body-and-param")
                    .param("size", "5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": "),
            false),
        new Scenario(
            "validation-failed-body",
            () ->
                post("/api/test/body")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"address\": {\"street\": \"\", \"postal_code\": \"ABC\"},"
                            + " \"items\": [{\"name\": \"much too long\"}],"
                            + " \"labels\": {\"a/b~c\": \"\"}}"),
            true),
        new Scenario(
            "validation-failed-class-level",
            () ->
                post("/api/test/range")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\": 2, \"to\": 1}"),
            true),
        new Scenario(
            "validation-failed-body-and-parameter",
            () ->
                post("/api/test/body-and-param")
                    .param("size", "5000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"\"}"),
            true));
  }

  /**
   * Validates a response body against one of the vendored schemas.
   *
   * <p>The whole document is handed to the validator with a top-level {@code $ref} added, so the
   * schema's internal {@code $ref}s resolve inside the same document, exactly as they do in the
   * OpenAPI file.
   *
   * @param schemaName {@code Problem} or {@code ValidationProblem}
   * @param body the response body
   * @return the validation errors, empty when the body is valid
   */
  private static List<Error> validate(final String schemaName, final JsonNode body) {
    final ObjectNode root = (ObjectNode) schemaDocument.deepCopy();
    root.put("$ref", "#/components/schemas/" + schemaName);
    final Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(root.toString());
    return schema.validate(body);
  }

  private static MockMvc mockMvc(final WebApplicationContext context) {
    final Filter traceFilter =
        context.getBean("traceContextFilterRegistration", FilterRegistrationBean.class).getFilter();
    return MockMvcBuilders.webAppContextSetup(context).addFilters(traceFilter).build();
  }

  /**
   * One error scenario.
   *
   * @param name a readable name, used in failure messages
   * @param request builds the request that triggers the error
   * @param validationProblem whether the response must satisfy {@code ValidationProblem} rather than
   *     {@code Problem} — true exactly for body-validation failures, which carry {@code errors}
   */
  record Scenario(
      String name,
      java.util.function.Supplier<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>
          request,
      boolean validationProblem) {

    @Override
    public String toString() {
      return name;
    }
  }
}
