package com.openelements.spring.base.problem;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * Renders the sentences that describe invalid query, path and header parameters.
 *
 * <p>Parameter failures deliberately do <strong>not</strong> become {@code errors} entries. The
 * GovStack {@code FieldError} schema requires a {@code pointer}, and a JSON pointer addresses a
 * location in the request <em>body</em> — a query parameter has none. GovStack API Design Guide
 * §11.4 allows a building block to describe invalid parameters in {@code detail} instead, which is
 * what this class produces:
 *
 * <pre>{@code
 * Invalid query parameter 'size': must be less than or equal to 100.
 * Missing required query parameter 'size'.
 * Invalid path parameter 'id': must be a valid UUID.
 * }</pre>
 *
 * <p>One sentence per parameter, joined by a space, in declaration order. Rejected values are never
 * part of a sentence: a parameter value routinely carries an e-mail address or a search term, and
 * {@code detail} is echoed to the client and into logs.
 *
 * <p>The class is shared by the MVC exception handler and the catch-all resolver so the wording
 * exists once.
 */
final class ParameterMessages {

  private ParameterMessages() {
    throw new AssertionError("ParameterMessages must not be instantiated");
  }

  /**
   * Describes a parameter whose value was rejected.
   *
   * @param location where the parameter was read from — {@code query}, {@code path}, {@code header}
   * @param name the parameter name as the client wrote it
   * @param reason why the value was rejected, without the value itself; a {@code null} or blank
   *     reason falls back to a generic one rather than writing {@code null} into the response
   * @return one sentence, ending in a full stop
   */
  static String invalid(
      final String location, final String name, final @Nullable String reason) {
    return "Invalid " + location + " parameter '" + name + "': " + withoutTrailingStop(reason) + ".";
  }

  /**
   * Describes a required parameter that was not sent at all.
   *
   * @param location where the parameter was expected — {@code query}, {@code path}, {@code header}
   * @param name the parameter name
   * @return one sentence, ending in a full stop
   */
  static String missing(final String location, final String name) {
    return "Missing required " + location + " parameter '" + name + "'.";
  }

  /**
   * Describes a parameter whose value could not be converted to the expected type.
   *
   * <p>Only the <em>expected</em> type is named. Spring's own message for this failure is {@code
   * Failed to convert 'size' with value: 'jane@example.com'} — it echoes the rejected value, which
   * is exactly what must not reach the client.
   *
   * @param location where the parameter was read from
   * @param name the parameter name
   * @param requiredType the type the value should have had, or {@code null} if unknown
   * @return one sentence, ending in a full stop
   */
  static String typeMismatch(
      final String location, final String name, final Class<?> requiredType) {
    final String reason =
        requiredType != null
            ? "must be a valid " + requiredType.getSimpleName()
            : "must be a valid value for this parameter";
    return invalid(location, name, reason);
  }

  /**
   * Joins the sentences of several parameters into one {@code detail}.
   *
   * @param sentences the sentences, in declaration order
   * @return the joined text, or {@code null} when there is nothing to say
   */
  static String join(final List<String> sentences) {
    Objects.requireNonNull(sentences, "sentences must not be null");
    return sentences.isEmpty() ? null : String.join(" ", sentences);
  }

  /**
   * Determines where a handler-method parameter is read from.
   *
   * @param parameter the handler-method parameter
   * @return {@code query}, {@code path}, {@code header}, {@code part}, or {@code request} when the
   *     parameter carries none of the binding annotations
   */
  static String location(final MethodParameter parameter) {
    if (hasAnnotation(parameter, PathVariable.class)) {
      return "path";
    }
    if (hasAnnotation(parameter, RequestHeader.class)) {
      return "header";
    }
    if (hasAnnotation(parameter, RequestPart.class)) {
      return "part";
    }
    if (hasAnnotation(parameter, RequestParam.class)) {
      return "query";
    }
    return "request";
  }

  /**
   * Reads the name a parameter is bound under, preferring the one the annotation declares.
   *
   * @param parameter the handler-method parameter
   * @return the name the client uses
   */
  static String name(final MethodParameter parameter) {
    final String declared = declaredName(parameter);
    if (declared != null && !declared.isBlank()) {
      return declared;
    }
    final String javaName = parameter.getParameterName();
    return javaName != null ? javaName : "parameter " + parameter.getParameterIndex();
  }

  private static String declaredName(final MethodParameter parameter) {
    final PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
    if (pathVariable != null) {
      return pathVariable.name().isBlank() ? pathVariable.value() : pathVariable.name();
    }
    final RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
    if (header != null) {
      return header.name().isBlank() ? header.value() : header.name();
    }
    final RequestPart part = parameter.getParameterAnnotation(RequestPart.class);
    if (part != null) {
      return part.name().isBlank() ? part.value() : part.name();
    }
    final RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam != null) {
      return requestParam.name().isBlank() ? requestParam.value() : requestParam.name();
    }
    return null;
  }

  private static boolean hasAnnotation(
      final MethodParameter parameter, final Class<? extends Annotation> annotation) {
    return parameter.hasParameterAnnotation(annotation);
  }

  /**
   * Strips a trailing full stop so {@link #invalid} never produces {@code "..".}
   *
   * @param reason the raw reason text
   * @return the reason without a trailing full stop
   */
  private static String withoutTrailingStop(final @Nullable String reason) {
    final String trimmed = reason == null ? "" : reason.strip();
    if (trimmed.isEmpty()) {
      return "is not valid";
    }
    return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }
}
