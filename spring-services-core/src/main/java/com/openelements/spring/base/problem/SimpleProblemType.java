package com.openelements.spring.base.problem;

import org.springframework.http.HttpStatus;

/**
 * The {@link ProblemType} returned by {@link ProblemType#of(String, HttpStatus, String)}.
 *
 * <p>Package-private: applications hold the interface, not this type.
 *
 * @param slug the stable identifier
 * @param status the HTTP status
 * @param title the human-readable summary
 */
record SimpleProblemType(String slug, HttpStatus status, String title) implements ProblemType {

  /**
   * Builds a validated instance.
   *
   * <p>The check runs after construction rather than in a compact constructor, so it can reuse
   * {@link ProblemType#validate(ProblemType)} — the same method application enums are told to call —
   * instead of duplicating the rules. Nothing outside this package can construct the record without
   * passing through here.
   *
   * @param slug the stable identifier
   * @param status the HTTP status
   * @param title the human-readable summary
   * @return the validated problem type
   * @throws NullPointerException if any argument is {@code null}
   * @throws IllegalArgumentException if any argument violates the {@link ProblemType} contract
   */
  static SimpleProblemType create(final String slug, final HttpStatus status, final String title) {
    final SimpleProblemType type = new SimpleProblemType(slug, status, title);
    ProblemType.validate(type);
    return type;
  }
}
