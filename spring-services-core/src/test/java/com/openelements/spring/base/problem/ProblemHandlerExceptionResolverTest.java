package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link ProblemHandlerExceptionResolver} — the last resort.
 *
 * <h2>What is tested</h2>
 *
 * <p>The resolution order and, above all, the two decisions with consequences:
 *
 * <ul>
 *   <li><b>An unexpected exception tells the client nothing.</b> The fixture throws an {@code
 *       IllegalStateException} whose message is a SQL statement containing an e-mail address — the
 *       realistic shape of a leak. The test asserts that neither the message, nor any fragment of
 *       it, nor the exception's class name appears anywhere in the response.
 *   <li><b>A {@code ConstraintViolationException} is only a {@code 400} at the controller
 *       boundary.</b> The same exception type means two completely different things: a client sent
 *       an unusable parameter, or the application called its own {@code @Validated} service with an
 *       argument it built wrong. Answering {@code 400} for the second would blame the caller for a
 *       server-side defect and hide the bug behind a client error in every dashboard.
 *   <li><b>{@link AccessDeniedException} is not resolved at all</b>, so Spring Security's {@code
 *       ExceptionTranslationFilter} keeps deciding between {@code 401} for an anonymous caller and
 *       {@code 403} for an authenticated one.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5. The resolver is driven directly with {@link MockHttpServletRequest} and {@link
 * MockHttpServletResponse}, and the written JSON is parsed back — so the assertions are about what a
 * client receives, not about an intermediate object.
 *
 * <p>The constraint violations come from a real {@link Validator} executing a real method, because
 * the controller-boundary check reads the violation's root bean and its executable node; a
 * hand-built violation would prove nothing about it.
 *
 * <p><b>Mock-Audit.</b> No behaviour mocks. {@code MockHttpServletRequest} / {@code
 * MockHttpServletResponse} are Spring's servlet-API test doubles.
 */
@DisplayName("ProblemHandlerExceptionResolver")
class ProblemHandlerExceptionResolverTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private static ValidatorFactory validatorFactory;

  private final ObjectMapper objectMapper = new JsonMapper();
  private final ProblemHandlerExceptionResolver resolver =
      new ProblemHandlerExceptionResolver(
          new DefaultProblemFactory(new ProblemDetailsProperties(true, BASE, true)), objectMapper);

  @BeforeAll
  static void startValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void stopValidator() {
    validatorFactory.close();
  }

  private JsonNode resolve(final Exception ex, final Object handler) throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    final MockHttpServletResponse response = new MockHttpServletResponse();

    final var modelAndView = resolver.resolveException(request, response, handler, ex);

    assertThat(modelAndView).as("the resolver must report the exception as handled").isNotNull();
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    final JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("status").asInt()).isEqualTo(response.getStatus());
    return body;
  }

  @Test
  @DisplayName("A ProblemException keeps its own type, detail and extension members.")
  void problemExceptionKeepsItsType() throws Exception {
    final var type = ProblemType.of("duplicate-record", HttpStatus.CONFLICT, "Duplicate record");

    final JsonNode body =
        resolve(
            new ProblemException(type, "Record already exists", java.util.Map.of("existingId", "42")),
            null);

    assertThat(body.path("type").asString()).isEqualTo(BASE + "duplicate-record");
    assertThat(body.path("status").asInt()).isEqualTo(409);
    assertThat(body.path("detail").asString()).isEqualTo("Record already exists");
    assertThat(body.path("existingId").asString()).isEqualTo("42");
  }

  @Test
  @DisplayName("@ResponseStatus wins over internal-error and contributes its reason.")
  void responseStatusIsHonoured() throws Exception {
    final JsonNode body = resolve(new AlreadyTakenException(), null);

    assertThat(body.path("status").asInt()).isEqualTo(409);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "conflict");
    assertThat(body.path("detail").asString()).isEqualTo("Already taken");
  }

  @Test
  @DisplayName("A ResponseStatusException contributes its status and its reason.")
  void responseStatusExceptionIsHonoured() throws Exception {
    final JsonNode body =
        resolve(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"), null);

    assertThat(body.path("status").asInt()).isEqualTo(404);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "not-found");
    assertThat(body.path("detail").asString()).isEqualTo("Contact not found");
  }

  /** The shape {@code TranslationService} throws when an upstream call fails. */
  @Test
  @DisplayName("A 502 from an upstream call becomes bad-gateway.")
  void badGatewayIsHonoured() throws Exception {
    final JsonNode body =
        resolve(
            new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Translation API call failed"), null);

    assertThat(body.path("status").asInt()).isEqualTo(502);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "bad-gateway");
    assertThat(body.path("detail").asString()).isEqualTo("Translation API call failed");
  }

  @Test
  @DisplayName("A status Spring cannot name keeps its own code and gets the http-<code> type.")
  void unknownStatusKeepsItsCode() throws Exception {
    final JsonNode body =
        resolve(new ResponseStatusException(HttpStatusCode.valueOf(418)), null);

    assertThat(body.path("status").asInt()).isEqualTo(418);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "http-418");
    assertThat(body.path("title").asString()).isEqualTo("HTTP 418");
  }

  @Test
  @DisplayName("An unexpected exception leaks neither its message nor its class name.")
  void unexpectedExceptionLeaksNothing() throws Exception {
    final String secret = "SELECT * FROM users WHERE email='jane@example.com'";

    final JsonNode body = resolve(new IllegalStateException(secret), null);

    assertThat(body.path("status").asInt()).isEqualTo(500);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "internal-error");
    assertThat(body.path("detail").asString())
        .isEqualTo(ProblemHandlerExceptionResolver.INTERNAL_ERROR_DETAIL);
    assertThat(body.toString())
        .doesNotContain("jane@example.com")
        .doesNotContain("SELECT")
        .doesNotContain("users")
        .doesNotContain("IllegalStateException");
  }

  @Test
  @DisplayName("A violation raised on the handler method itself is a 400 invalid-parameter.")
  void controllerBoundaryViolationIsABadRequest() throws Exception {
    final TestController controller = new TestController();
    final Method method = TestController.class.getDeclaredMethod("list", int.class);
    final var violations =
        validatorFactory
            .getValidator()
            .forExecutables()
            .validateParameters(controller, method, new Object[] {5000});
    assertThat(violations).isNotEmpty();

    final JsonNode body =
        resolve(
            new ConstraintViolationException(violations),
            new HandlerMethod(controller, method));

    assertThat(body.path("status").asInt()).isEqualTo(400);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "invalid-parameter");
    assertThat(body.path("detail").asString()).contains("size");
  }

  @Test
  @DisplayName("A violation from a service deeper down is a 500 — it is a server-side defect.")
  void serviceLayerViolationIsAServerError() throws Exception {
    final TestService service = new TestService();
    final Method method = TestService.class.getDeclaredMethod("process", int.class);
    final var violations =
        validatorFactory
            .getValidator()
            .forExecutables()
            .validateParameters(service, method, new Object[] {5000});
    assertThat(violations).isNotEmpty();

    final TestController controller = new TestController();
    final JsonNode body =
        resolve(
            new ConstraintViolationException(violations),
            new HandlerMethod(controller, TestController.class.getDeclaredMethod("list", int.class)));

    assertThat(body.path("status").asInt()).isEqualTo(500);
    assertThat(body.path("type").asString()).isEqualTo(BASE + "internal-error");
  }

  @Test
  @DisplayName("A ConstraintViolationException without a handler method is a 500.")
  void violationWithoutHandlerIsAServerError() throws Exception {
    final JsonNode body = resolve(new ConstraintViolationException(Set.of()), null);

    assertThat(body.path("status").asInt()).isEqualTo(500);
  }

  @Test
  @DisplayName("AccessDeniedException is left to Spring Security.")
  void accessDeniedIsNotResolved() {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    final MockHttpServletResponse response = new MockHttpServletResponse();

    final var modelAndView =
        resolver.resolveException(
            request, response, null, new AccessDeniedException("Access Denied"));

    assertThat(modelAndView).isNull();
    assertThat(response.getContentAsByteArray()).isEmpty();
  }

  // --- fixtures ---------------------------------------------------------------------------------

  /** Stands in for an application exception that carries its own status. */
  @ResponseStatus(value = HttpStatus.CONFLICT, reason = "Already taken")
  private static final class AlreadyTakenException extends RuntimeException {}

  /** Stands in for a {@code @Validated} controller. */
  static final class TestController {
    void list(@Max(100) final int size) {}
  }

  /** Stands in for a {@code @Validated} service called by the controller. */
  static final class TestService {
    void process(@Max(100) final int size) {}
  }
}
