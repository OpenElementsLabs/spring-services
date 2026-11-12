package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link ProblemType} — the contract every problem type has to satisfy.
 *
 * <h2>What is tested</h2>
 *
 * <p>The validation performed by {@link ProblemType#of(String, HttpStatus, String)} and {@link
 * ProblemType#validate(ProblemType)}. Those rules are not cosmetic: the slug ends up inside a
 * {@code type} URI that clients compare literally, and the GovStack {@code Problem} schema pins the
 * slug's shape with the pattern {@code [a-z][a-z0-9]*(?:-[a-z0-9]+)*}. A type that slips through
 * with {@code Duplicate_Contact} would produce responses that fail a consumer's schema validation,
 * at runtime, in production — so the rejection has to happen at construction.
 *
 * <p>The status rule exists for the same reason: a problem is by definition an error, and a {@code
 * 200} problem type would let a handler answer a success status with a problem body.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context, no mocks. The invalid-slug matrix is the one named in {@code
 * behaviors.md}, one case per line.
 */
@DisplayName("ProblemType")
class ProblemTypeTest {

  @Test
  @DisplayName("A valid type returns slug, status and title unchanged.")
  void validTypeKeepsItsParts() {
    final ProblemType type =
        ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact");

    assertThat(type.slug()).isEqualTo("duplicate-contact");
    assertThat(type.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(type.title()).isEqualTo("Duplicate contact");
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(
      strings = {
        "Duplicate",
        "duplicate_contact",
        "-duplicate",
        "duplicate-",
        "duplicate--contact",
        "",
        " ",
        "duplicate contact",
        "duplicate.contact",
        "1duplicate",
      })
  @DisplayName("A slug that is not kebab-case is rejected.")
  void invalidSlugRejected(final String slug) {
    assertThatThrownBy(() -> ProblemType.of(slug, HttpStatus.CONFLICT, "Title"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slug");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(ints = {200, 201, 204, 301, 302, 100})
  @DisplayName("A status outside 4xx/5xx is rejected — a problem is by definition an error.")
  void nonErrorStatusRejected(final int statusCode) {
    final HttpStatus status = HttpStatus.valueOf(statusCode);
    assertThatThrownBy(() -> ProblemType.of("some-problem", status, "Title"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status");
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(strings = {"", " ", "\t", "\n"})
  @DisplayName("A blank title is rejected — it is the only human-readable part of the type.")
  void blankTitleRejected(final String title) {
    assertThatThrownBy(() -> ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, title))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  @DisplayName("Null arguments are rejected with NullPointerException.")
  void nullArgumentsRejected() {
    assertThatThrownBy(() -> ProblemType.of(null, HttpStatus.CONFLICT, "Title"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ProblemType.of("slug", null, "Title"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ProblemType.of("slug", HttpStatus.CONFLICT, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ProblemType.validate(null)).isInstanceOf(NullPointerException.class);
  }

  /**
   * An application enum implements the interface directly and never passes through {@link
   * ProblemType#of}. {@link ProblemType#validate(ProblemType)} is what such an enum is told to call
   * from its own test, so it has to apply the identical rules.
   */
  @Test
  @DisplayName("validate(...) applies the same rules to a hand-written implementation.")
  void validateChecksHandWrittenImplementations() {
    assertThat(catchThrowableOf(new TestProblemType("ok-type", HttpStatus.CONFLICT, "Fine")))
        .isNull();
    assertThatThrownBy(
            () -> ProblemType.validate(new TestProblemType("Bad_Slug", HttpStatus.CONFLICT, "T")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProblemType.validate(new TestProblemType("ok", HttpStatus.OK, "T")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> ProblemType.validate(new TestProblemType("ok", HttpStatus.CONFLICT, " ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Two types with the same parts are equal — constants can be compared by value.")
  void equalPartsMeanEqualTypes() {
    assertThat(ProblemType.of("a-b", HttpStatus.CONFLICT, "T"))
        .isEqualTo(ProblemType.of("a-b", HttpStatus.CONFLICT, "T"))
        .hasSameHashCodeAs(ProblemType.of("a-b", HttpStatus.CONFLICT, "T"));
  }

  private static Throwable catchThrowableOf(final ProblemType type) {
    try {
      ProblemType.validate(type);
      return null;
    } catch (final RuntimeException e) {
      return e;
    }
  }

  /** A hand-written implementation, standing in for an application's enum constant. */
  private record TestProblemType(String slug, HttpStatus status, String title)
      implements ProblemType {}
}
