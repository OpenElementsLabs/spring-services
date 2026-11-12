package com.openelements.spring.base.problem;

import jakarta.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Builds the {@code errors} array of a {@code validation-failed} problem.
 *
 * <p>Each entry is {@code {"pointer", "message"}} — the shape of the GovStack {@code FieldError}
 * schema:
 *
 * <pre>{@code
 * "errors": [
 *   { "pointer": "/address/postal_code", "message": "must match \"[0-9]{5}\"" }
 * ]
 * }</pre>
 *
 * <p>The pointer comes from {@link JsonPointers} whenever Spring kept the underlying {@link
 * ConstraintViolation} around, which it does for every Bean Validation failure. That is the path
 * that honours {@code @JsonProperty} and the configured naming strategy. A failure reported by a
 * plain Spring {@link org.springframework.validation.Validator} has no constraint violation to
 * unwrap, so its field expression is converted structurally instead — the best available answer, and
 * still a valid pointer.
 *
 * <p><strong>Rejected values are never included.</strong> {@link FieldError#getRejectedValue()} is
 * not read anywhere in this class: the value the client sent is frequently the personal data the
 * response must not echo. The only text that reaches the client is the interpolated Bean Validation
 * message — which a developer can still make leak by writing {@code ${validatedValue}} into a
 * message template, and which is therefore documented as the developer's responsibility.
 */
final class BodyErrors {

  /** Member names of one {@code errors} entry. */
  private static final String POINTER = "pointer";

  private static final String MESSAGE = "message";

  private final JsonPointers jsonPointers;

  BodyErrors(final JsonPointers jsonPointers) {
    this.jsonPointers = Objects.requireNonNull(jsonPointers, "jsonPointers must not be null");
  }

  /**
   * Converts the failures of one validated body into {@code errors} entries.
   *
   * @param errors the binding result of the request body
   * @return one entry per failure, in the order the validator reported them; never {@code null}
   */
  List<Map<String, String>> from(final Errors errors) {
    Objects.requireNonNull(errors, "errors must not be null");
    final Class<?> rootType = rootType(errors);

    final List<Map<String, String>> entries = new ArrayList<>();
    for (final ObjectError error : errors.getAllErrors()) {
      entries.add(entry(pointer(error, rootType), message(error)));
    }
    return List.copyOf(entries);
  }

  /**
   * Determines the JSON pointer of one failure.
   *
   * @param error the reported failure
   * @param rootType the Java type of the request body
   * @return an RFC 6901 pointer; {@code ""} for a constraint on the body as a whole
   */
  private String pointer(final ObjectError error, final Class<?> rootType) {
    if (error.contains(ConstraintViolation.class)) {
      final ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);
      return jsonPointers.pointer(rootType, violation.getPropertyPath());
    }
    if (error instanceof FieldError fieldError) {
      return fromFieldExpression(fieldError.getField());
    }
    return "";
  }

  /**
   * Converts a Spring field expression into a pointer, for failures that carry no constraint
   * violation.
   *
   * <p>{@code items[0].name} becomes {@code /items/0/name}. The JSON names of the request cannot be
   * recovered here — there is no property path to resolve — so the Java names are used.
   *
   * @param field the field expression
   * @return an RFC 6901 pointer
   */
  private static String fromFieldExpression(final String field) {
    if (field == null || field.isBlank()) {
      return "";
    }
    final StringBuilder pointer = new StringBuilder();
    for (final String segment : field.replace("[", ".").replace("]", "").split("\\.")) {
      if (!segment.isEmpty()) {
        pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
      }
    }
    return pointer.toString();
  }

  /**
   * Reads the human-readable message of a failure.
   *
   * @param error the reported failure
   * @return the interpolated validation message, or the error code when there is none
   */
  private static String message(final ObjectError error) {
    final String message = error.getDefaultMessage();
    return message != null && !message.isBlank() ? message : String.valueOf(error.getCode());
  }

  /**
   * Determines the Java type of the validated body.
   *
   * @param errors the binding result
   * @return the target's class, or {@link Object} when the target is not available
   */
  private static Class<?> rootType(final Errors errors) {
    final Object target = errors instanceof org.springframework.validation.BindingResult result
        ? result.getTarget()
        : null;
    return target != null ? target.getClass() : Object.class;
  }

  /**
   * Builds one {@code errors} entry, keeping {@code pointer} before {@code message} so the JSON is
   * deterministic.
   *
   * @param pointer the JSON pointer
   * @param message the human-readable message
   * @return the entry
   */
  private static Map<String, String> entry(final String pointer, final String message) {
    final Map<String, String> entry = new LinkedHashMap<>();
    entry.put(POINTER, pointer);
    entry.put(MESSAGE, message);
    return java.util.Collections.unmodifiableMap(entry);
  }
}
