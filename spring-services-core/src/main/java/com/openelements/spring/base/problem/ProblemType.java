package com.openelements.spring.base.problem;

import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * One kind of error, identified by a stable slug.
 *
 * <p>A problem type is the machine-readable half of an RFC 9457 problem. The slug is appended to the
 * configured base URI ({@code openelements.problem-details.type-base}) to form the {@code type}
 * member of the response:
 *
 * <pre>{@code
 * https://open-elements.com/problems/spring-services/ + duplicate-contact
 * → "type": "https://open-elements.com/problems/spring-services/duplicate-contact"
 * }</pre>
 *
 * <p><strong>Clients compare the full {@code type} URI</strong>, never the {@code status} or the
 * {@code title}. A slug is therefore public API: changing one breaks every consumer that branches on
 * it, exactly like renaming a REST path.
 *
 * <h2>Defining application types</h2>
 *
 * <p>An enum is the usual form, because it gives the application one place that lists every error it
 * can produce:
 *
 * <pre>{@code
 * public enum CrmProblemType implements ProblemType {
 *   DUPLICATE_CONTACT("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact"),
 *   CONTACT_LOCKED("contact-locked", HttpStatus.LOCKED, "Contact locked");
 *
 *   private final String slug;
 *   private final HttpStatus status;
 *   private final String title;
 *
 *   CrmProblemType(String slug, HttpStatus status, String title) {
 *     this.slug = slug;
 *     this.status = status;
 *     this.title = title;
 *   }
 *
 *   @Override public String slug() { return slug; }
 *   @Override public HttpStatus status() { return status; }
 *   @Override public String title() { return title; }
 * }
 * }</pre>
 *
 * <p>For a handful of types, constants built with {@link #of(String, HttpStatus, String)} do just as
 * well. An enum cannot be a record and a record cannot be an enum, which is why this is an interface
 * rather than a record: both shapes have to be possible.
 *
 * <p>An enum that builds its constants by hand bypasses the validation {@link #of(String,
 * HttpStatus, String)} performs. Call {@link #validate(ProblemType)} from a test — or from the enum
 * constructor — to get the same guarantees.
 *
 * <p>Implementations must be immutable and thread-safe.
 */
public interface ProblemType {

  /** Slugs are kebab-case: lowercase alphanumerics in groups separated by single hyphens. */
  Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

  /**
   * Returns the stable identifier of this problem type.
   *
   * <p>Kebab-case, matching {@link #SLUG_PATTERN}. Part of the public contract — see the class
   * documentation.
   *
   * @return the slug, e.g. {@code duplicate-contact}
   */
  String slug();

  /**
   * Returns the HTTP status this problem type is answered with.
   *
   * @return a 4xx or 5xx status
   */
  HttpStatus status();

  /**
   * Returns the short, human-readable summary of this problem type.
   *
   * <p>English and stable: it describes the type, not the occurrence. Per RFC 9457 §3.1.2 it must
   * not change from occurrence to occurrence — the occurrence-specific text belongs in {@code
   * detail}. The library does not localise titles.
   *
   * @return the title, e.g. {@code Duplicate contact}
   */
  String title();

  /**
   * Creates a problem type from its three parts.
   *
   * @param slug the stable identifier, kebab-case per {@link #SLUG_PATTERN}
   * @param status a 4xx or 5xx status
   * @param title a non-blank, English summary of the type
   * @return an immutable problem type
   * @throws NullPointerException if any argument is {@code null}
   * @throws IllegalArgumentException if the slug is not kebab-case, the status is not an error
   *     status, or the title is blank
   */
  static ProblemType of(final String slug, final HttpStatus status, final String title) {
    return SimpleProblemType.create(slug, status, title);
  }

  /**
   * Checks that a problem type satisfies the contract of {@link #slug()}, {@link #status()} and
   * {@link #title()}.
   *
   * <p>Intended for implementations that do not go through {@link #of(String, HttpStatus, String)} —
   * typically an application enum. Calling it from a test over {@code values()} turns a typo in a
   * slug into a build failure instead of a response no client recognises.
   *
   * @param type the type to check
   * @throws NullPointerException if {@code type} or any of its parts is {@code null}
   * @throws IllegalArgumentException if the slug is not kebab-case, the status is not an error
   *     status, or the title is blank
   */
  static void validate(final ProblemType type) {
    Objects.requireNonNull(type, "type must not be null");
    final String slug = Objects.requireNonNull(type.slug(), "slug must not be null");
    final HttpStatus status = Objects.requireNonNull(type.status(), "status must not be null");
    final String title = Objects.requireNonNull(type.title(), "title must not be null");

    if (!SLUG_PATTERN.matcher(slug).matches()) {
      throw new IllegalArgumentException(
          "slug must be kebab-case (" + SLUG_PATTERN.pattern() + ") but was '" + slug + "'");
    }
    if (!status.isError()) {
      throw new IllegalArgumentException("status must be a 4xx or 5xx status but was " + status);
    }
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
  }
}
