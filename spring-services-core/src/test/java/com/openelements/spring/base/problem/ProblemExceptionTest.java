package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link ProblemException} — the application's short path to a typed problem.
 *
 * <h2>What is tested</h2>
 *
 * <p>The constructor's guarantees, because everything downstream assumes them:
 *
 * <ul>
 *   <li><b>Reserved member names are rejected.</b> An extension called {@code status} would
 *       overwrite the problem's own {@code status} member and produce a body that contradicts the
 *       HTTP status line. Catching it at the throw site turns a confusing response into a failing
 *       test.
 *   <li><b>{@code null} values are rejected.</b> A {@code null} extension serialises to {@code
 *       "key": null}, which says nothing and cannot be distinguished from "absent".
 *   <li><b>The properties map is copied and unmodifiable, and keeps its order</b>, so a caller
 *       cannot mutate a problem after throwing it and the JSON member order is deterministic.
 *   <li><b>{@code getMessage()} falls back to the title</b>, so a problem without a detail still
 *       logs as something other than {@code null}.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context, no mocks.
 */
@DisplayName("ProblemException")
class ProblemExceptionTest {

  private static final ProblemType TYPE =
      ProblemType.of("duplicate-record", HttpStatus.CONFLICT, "Duplicate record");

  @Test
  @DisplayName("Type, detail and properties are returned as given.")
  void partsAreReturned() {
    final ProblemException exception =
        new ProblemException(TYPE, "Record already exists", Map.of("existingId", "42"));

    assertThat(exception.type()).isEqualTo(TYPE);
    assertThat(exception.detail()).contains("Record already exists");
    assertThat(exception.properties()).containsExactly(Map.entry("existingId", "42"));
  }

  @Test
  @DisplayName("Without a detail, detail() is empty and getMessage() is the title.")
  void withoutDetailTheTitleIsTheMessage() {
    final ProblemException exception = new ProblemException(TYPE);

    assertThat(exception.detail()).isEmpty();
    assertThat(exception.getMessage()).isEqualTo("Duplicate record");
    assertThat(exception.properties()).isEmpty();
  }

  @Test
  @DisplayName("With a detail, getMessage() is the detail.")
  void withDetailTheDetailIsTheMessage() {
    assertThat(new ProblemException(TYPE, "Record already exists").getMessage())
        .isEqualTo("Record already exists");
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(strings = {"type", "title", "status", "detail", "instance", "traceId", "errors"})
  @DisplayName("A reserved member name is rejected — it would overwrite what the library writes.")
  void reservedMemberNamesRejected(final String reserved) {
    assertThatThrownBy(() -> new ProblemException(TYPE, "detail", Map.of(reserved, "value")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(reserved);
  }

  @Test
  @DisplayName("A null property value is rejected and the message names the key.")
  void nullPropertyValueRejected() {
    final Map<String, Object> properties = new HashMap<>();
    properties.put("existingId", null);

    assertThatThrownBy(() -> new ProblemException(TYPE, "detail", properties))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("existingId");
  }

  @Test
  @DisplayName("A null property name is rejected.")
  void nullPropertyNameRejected() {
    final Map<String, Object> properties = new HashMap<>();
    properties.put(null, "value");

    assertThatThrownBy(() -> new ProblemException(TYPE, "detail", properties))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("A null type is rejected.")
  void nullTypeRejected() {
    assertThatThrownBy(() -> new ProblemException(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("The properties map is copied, unmodifiable and keeps the caller's order.")
  void propertiesAreCopiedUnmodifiableAndOrdered() {
    final Map<String, Object> source = new LinkedHashMap<>();
    source.put("b", 2);
    source.put("a", 1);
    source.put("c", 3);

    final ProblemException exception = new ProblemException(TYPE, "detail", source);
    source.put("d", 4);

    assertThat(exception.properties()).containsExactly(
        Map.entry("b", 2), Map.entry("a", 1), Map.entry("c", 3));
    assertThatThrownBy(() -> exception.properties().put("e", 5))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /**
   * The class is deliberately not {@code final} so an application can give a domain exception a
   * problem type. The subclass must behave exactly like the base class without overriding anything.
   */
  @Test
  @DisplayName("A subclass carries its type through unchanged.")
  void subclassCarriesItsType() {
    final ProblemException exception = new DuplicateRecordException("Record already exists");

    assertThat(exception).isInstanceOf(ProblemException.class);
    assertThat(exception.type()).isEqualTo(TYPE);
    assertThat(exception.detail()).contains("Record already exists");
  }

  /** Stands in for an application's domain exception. */
  private static final class DuplicateRecordException extends ProblemException {
    private DuplicateRecordException(final String detail) {
      super(TYPE, detail);
    }
  }
}
