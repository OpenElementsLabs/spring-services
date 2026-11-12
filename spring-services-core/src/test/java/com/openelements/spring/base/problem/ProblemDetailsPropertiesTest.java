package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link ProblemDetailsProperties}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That an unusable {@code type-base} is rejected at binding time rather than silently producing
 * broken {@code type} URIs. Two forms matter:
 *
 * <ul>
 *   <li>a <b>relative</b> base ({@code /problems/}) yields a relative {@code type}, which RFC 9457
 *       permits but every consumer resolves against a different document base — so the same error
 *       would carry a different identity depending on who read it;
 *   <li>a base <b>without a trailing slash</b> ({@code https://example.org/problems}) concatenates
 *       straight into the slug: {@code https://example.org/problemsnot-found}.
 * </ul>
 *
 * <p>Both are configuration mistakes that are invisible until a client fails to match a type, which
 * is why they fail start-up and why the message names the property.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 against the record's own constructor, which is where Spring Boot's constructor
 * binding runs the validation. No Spring context, no mocks.
 */
@DisplayName("ProblemDetailsProperties")
class ProblemDetailsPropertiesTest {

  private static final String DEFAULT_BASE = "https://open-elements.com/problems/spring-services/";

  @Test
  @DisplayName("A valid absolute base with a trailing slash is accepted.")
  void validBaseAccepted() {
    final ProblemDetailsProperties properties =
        new ProblemDetailsProperties(true, DEFAULT_BASE, true);

    assertThat(properties.typeBase()).isEqualTo(DEFAULT_BASE);
    assertThat(properties.enabled()).isTrue();
    assertThat(properties.includeInstance()).isTrue();
  }

  @Test
  @DisplayName("typeUri(...) appends the slug to the base.")
  void typeUriAppendsTheSlug() {
    final ProblemDetailsProperties properties =
        new ProblemDetailsProperties(true, DEFAULT_BASE, true);

    assertThat(properties.typeUri(StandardProblemType.UNAUTHORIZED))
        .hasToString(DEFAULT_BASE + "unauthorized");
    assertThat(properties.typeUri(ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, "D")))
        .hasToString(DEFAULT_BASE + "duplicate-contact");
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(strings = {"/problems/", "problems/", "//example.org/problems/"})
  @DisplayName("A relative type-base is rejected and the message names the property.")
  void relativeBaseRejected(final String typeBase) {
    assertThatThrownBy(() -> new ProblemDetailsProperties(true, typeBase, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("openelements.problem-details.type-base")
        .hasMessageContaining("absolute");
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(strings = {"https://example.org/problems", "https://example.org"})
  @DisplayName("A type-base without a trailing slash is rejected.")
  void baseWithoutTrailingSlashRejected(final String typeBase) {
    assertThatThrownBy(() -> new ProblemDetailsProperties(true, typeBase, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("openelements.problem-details.type-base")
        .hasMessageContaining("'/'");
  }

  @Test
  @DisplayName("A syntactically broken type-base is rejected.")
  void malformedBaseRejected() {
    assertThatThrownBy(() -> new ProblemDetailsProperties(true, "https://exa mple.org/p/", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("openelements.problem-details.type-base");
  }

  @Test
  @DisplayName("A null type-base is rejected.")
  void nullBaseRejected() {
    assertThatThrownBy(() -> new ProblemDetailsProperties(true, null, true))
        .isInstanceOf(NullPointerException.class);
  }
}
