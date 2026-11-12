package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Unit tests for {@link ParameterMessages} — the wording of parameter failures.
 *
 * <h2>What is tested</h2>
 *
 * <p>The sentences are published behaviour: they are the entire {@code detail} of an {@code
 * invalid-parameter} problem, and {@code behaviors.md} spells one of them out character for
 * character ({@code Invalid query parameter 'size': must be less than or equal to 100.}). The tests
 * pin that wording so a reworded sentence is a deliberate change.
 *
 * <p>Two properties matter beyond the wording:
 *
 * <ul>
 *   <li><b>The rejected value never appears.</b> {@link ParameterMessages#typeMismatch} names only
 *       the expected type. Spring's own message for the same failure is {@code Failed to convert
 *       'size' with value: 'jane@example.com'}, which would put whatever the client sent — an
 *       e-mail address, a token, a search term — into the response body and the logs.
 *   <li><b>The location is read from the binding annotation</b>, including the name it declares, so
 *       a parameter bound as {@code X-Page} is named {@code X-Page} and not {@code page}.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5. The {@link MethodParameter}s are taken off a real method by reflection, so the
 * annotation reading is the production path. No mocks.
 */
@DisplayName("ParameterMessages")
class ParameterMessagesTest {

  private static MethodParameter parameterAt(final int index) throws NoSuchMethodException {
    final Method method =
        Fixtures.class.getDeclaredMethod("endpoint", int.class, long.class, int.class, String.class);
    final MethodParameter parameter = new MethodParameter(method, index);
    parameter.initParameterNameDiscovery(
        new org.springframework.core.DefaultParameterNameDiscoverer());
    return parameter;
  }

  @Test
  @DisplayName("An invalid value is described without the value.")
  void invalidSentenceMatchesTheSpecifiedWording() {
    assertThat(ParameterMessages.invalid("query", "size", "must be less than or equal to 100"))
        .isEqualTo("Invalid query parameter 'size': must be less than or equal to 100.");
  }

  @Test
  @DisplayName("A message that already ends in a full stop does not get a second one.")
  void trailingFullStopIsNotDoubled() {
    assertThat(ParameterMessages.invalid("query", "size", "must not be null."))
        .isEqualTo("Invalid query parameter 'size': must not be null.");
  }

  @Test
  @DisplayName("A missing parameter is named without inventing a reason.")
  void missingSentence() {
    assertThat(ParameterMessages.missing("query", "size"))
        .isEqualTo("Missing required query parameter 'size'.");
  }

  @Test
  @DisplayName("A type mismatch names the expected type and never the rejected value.")
  void typeMismatchNamesOnlyTheExpectedType() {
    final String sentence = ParameterMessages.typeMismatch("query", "size", Integer.class);

    assertThat(sentence).isEqualTo("Invalid query parameter 'size': must be a valid Integer.");
    assertThat(sentence).doesNotContain("jane@example.com");
  }

  @Test
  @DisplayName("An unknown expected type still produces a usable sentence.")
  void typeMismatchWithoutARequiredType() {
    assertThat(ParameterMessages.typeMismatch("path", "id", null))
        .isEqualTo("Invalid path parameter 'id': must be a valid value for this parameter.");
  }

  @Test
  @DisplayName("Sentences are joined by a single space, in the order given.")
  void sentencesAreJoinedInOrder() {
    assertThat(ParameterMessages.join(List.of("One.", "Two.", "Three.")))
        .isEqualTo("One. Two. Three.");
  }

  @Test
  @DisplayName("No sentences means no detail at all, rather than an empty string.")
  void noSentencesMeansNoDetail() {
    assertThat(ParameterMessages.join(List.of())).isNull();
  }

  @Test
  @DisplayName("The location is read from the binding annotation.")
  void locationComesFromTheAnnotation() throws Exception {
    assertThat(ParameterMessages.location(parameterAt(0))).isEqualTo("query");
    assertThat(ParameterMessages.location(parameterAt(1))).isEqualTo("path");
    assertThat(ParameterMessages.location(parameterAt(2))).isEqualTo("header");
    assertThat(ParameterMessages.location(parameterAt(3))).isEqualTo("request");
  }

  @Test
  @DisplayName("The name declared by the annotation wins over the Java parameter name.")
  void declaredNameWinsOverTheJavaName() throws Exception {
    assertThat(ParameterMessages.name(parameterAt(0))).isEqualTo("size");
    assertThat(ParameterMessages.name(parameterAt(1))).isEqualTo("id");
    assertThat(ParameterMessages.name(parameterAt(2))).isEqualTo("X-Page");
  }

  /** Carries the binding annotations under test; never invoked. */
  @SuppressWarnings("unused")
  private static final class Fixtures {
    private void endpoint(
        @RequestParam final int size,
        @PathVariable final long id,
        @RequestHeader("X-Page") final int page,
        final String unbound) {}
  }
}
