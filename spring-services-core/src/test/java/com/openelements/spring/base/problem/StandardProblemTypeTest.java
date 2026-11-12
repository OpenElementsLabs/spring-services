package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link StandardProblemType} — the library's frozen type table.
 *
 * <h2>What is tested</h2>
 *
 * <p>Three things, in order of how badly they would hurt if they broke:
 *
 * <ol>
 *   <li><b>The slugs are frozen.</b> {@link #slugsAreFrozen} spells out the slug of every constant a
 *       client is likely to branch on. These strings are published contract — a consumer compares
 *       {@code type} URIs — so the test exists to make a rename a deliberate act with a failing
 *       build, not a silent break. It also pins the two slugs that would have changed under us when
 *       Spring Framework 6 renamed {@code PAYLOAD_TOO_LARGE} and {@code UNPROCESSABLE_ENTITY}.
 *   <li><b>{@link StandardProblemType#forStatus(int)} resolves to the status-named constant</b>, not
 *       to one of the four cause-describing types that share a status with it. If {@code
 *       forStatus(400)} ever returned {@code validation-failed}, every plain {@code 400} would claim
 *       a body-validation failure and promise an {@code errors} array it does not have.
 *   <li><b>Every constant satisfies {@link ProblemType#validate}</b>, so a typo in a new constant is
 *       a build failure rather than an unparseable {@code type} URI in production.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context, no mocks.
 */
@DisplayName("StandardProblemType")
class StandardProblemTypeTest {

  @ParameterizedTest(name = "[{index}] {0} → {1} ({2})")
  @CsvSource({
    "BAD_REQUEST, bad-request, 400, Bad Request",
    "UNAUTHORIZED, unauthorized, 401, Unauthorized",
    "FORBIDDEN, forbidden, 403, Forbidden",
    "NOT_FOUND, not-found, 404, Not Found",
    "METHOD_NOT_ALLOWED, method-not-allowed, 405, Method Not Allowed",
    "NOT_ACCEPTABLE, not-acceptable, 406, Not Acceptable",
    "CONFLICT, conflict, 409, Conflict",
    "GONE, gone, 410, Gone",
    "CONTENT_TOO_LARGE, content-too-large, 413, Content Too Large",
    "UNSUPPORTED_MEDIA_TYPE, unsupported-media-type, 415, Unsupported Media Type",
    "UNPROCESSABLE_CONTENT, unprocessable-content, 422, Unprocessable Content",
    "TOO_MANY_REQUESTS, too-many-requests, 429, Too Many Requests",
    "INTERNAL_ERROR, internal-error, 500, Internal Server Error",
    "BAD_GATEWAY, bad-gateway, 502, Bad Gateway",
    "SERVICE_UNAVAILABLE, service-unavailable, 503, Service Unavailable",
    "GATEWAY_TIMEOUT, gateway-timeout, 504, Gateway Timeout",
    "VALIDATION_FAILED, validation-failed, 400, Validation failed",
    "MALFORMED_REQUEST, malformed-request, 400, Malformed request",
    "INVALID_PARAMETER, invalid-parameter, 400, Invalid parameter",
  })
  @DisplayName("The published slug, status and title of each constant are frozen.")
  void slugsAreFrozen(
      final StandardProblemType type, final String slug, final int status, final String title) {
    assertThat(type.slug()).isEqualTo(slug);
    assertThat(type.status().value()).isEqualTo(status);
    assertThat(type.title()).isEqualTo(title);
  }

  @ParameterizedTest(name = "[{index}] forStatus({0}) → {1}")
  @CsvSource({
    "400, BAD_REQUEST",
    "401, UNAUTHORIZED",
    "403, FORBIDDEN",
    "404, NOT_FOUND",
    "405, METHOD_NOT_ALLOWED",
    "409, CONFLICT",
    "413, CONTENT_TOO_LARGE",
    "415, UNSUPPORTED_MEDIA_TYPE",
    "422, UNPROCESSABLE_CONTENT",
    "429, TOO_MANY_REQUESTS",
    "500, INTERNAL_ERROR",
    "502, BAD_GATEWAY",
    "503, SERVICE_UNAVAILABLE",
  })
  @DisplayName("forStatus(...) returns the constant that names the status.")
  void forStatusReturnsTheStatusNamedConstant(
      final int statusCode, final StandardProblemType expected) {
    assertThat(StandardProblemType.forStatus(statusCode)).isEqualTo(expected);
  }

  @Test
  @DisplayName("forStatus(400) is bad-request, never one of the three cause-describing 400 types.")
  void forStatus400IsBadRequest() {
    assertThat(StandardProblemType.forStatus(400)).isEqualTo(StandardProblemType.BAD_REQUEST);
  }

  @ParameterizedTest(name = "[{index}] forStatus({0}) → http-{0}")
  @CsvSource({"418", "421", "499", "599"})
  @DisplayName("A status without a constant yields the synthesised http-<code> type.")
  void unknownStatusIsSynthesised(final int statusCode) {
    final ProblemType type = StandardProblemType.forStatus(statusCode);

    assertThat(type.slug()).isEqualTo("http-" + statusCode);
    assertThat(type.title()).isEqualTo("HTTP " + statusCode);
  }

  @Test
  @DisplayName("A synthesised type for a status Spring knows reports that status.")
  void synthesisedTypeKeepsAKnownStatus() {
    assertThat(StandardProblemType.forStatus(418).status()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
  }

  @ParameterizedTest
  @EnumSource(StandardProblemType.class)
  @DisplayName("Every constant satisfies the ProblemType contract.")
  void everyConstantIsValid(final StandardProblemType type) {
    ProblemType.validate(type);
  }

  @Test
  @DisplayName("No two constants share a slug.")
  void slugsAreUnique() {
    final long distinct =
        Arrays.stream(StandardProblemType.values()).map(StandardProblemType::slug).distinct().count();
    assertThat(distinct).isEqualTo(StandardProblemType.values().length);
  }

  /**
   * Only {@code 400} may be claimed by more than one constant — by {@code bad-request} and the three
   * cause-describing types. Any other duplicated status would make {@link
   * StandardProblemType#forStatus(int)} depend on declaration order.
   */
  @Test
  @DisplayName("Only 400 is claimed by more than one constant.")
  void onlyBadRequestStatusIsShared() {
    final var duplicated =
        Arrays.stream(StandardProblemType.values())
            .collect(Collectors.groupingBy(t -> t.status().value(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(java.util.Map.Entry::getKey)
            .toList();

    assertThat(duplicated).containsExactly(400);
  }
}
