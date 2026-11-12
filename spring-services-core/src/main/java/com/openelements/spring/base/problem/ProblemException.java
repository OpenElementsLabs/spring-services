package com.openelements.spring.base.problem;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Throw this to answer with a specific RFC 9457 problem.
 *
 * <p>This is the short path for an application: define a {@link ProblemType}, throw the exception,
 * and the library writes the response. No {@code @ExceptionHandler} is involved.
 *
 * <pre>{@code
 * throw new ProblemException(
 *     CrmProblemType.DUPLICATE_CONTACT,
 *     "A contact with this e-mail address already exists",
 *     Map.of("existingContactId", existing.id()));
 * }</pre>
 *
 * <p>produces
 *
 * <pre>{@code
 * HTTP/1.1 409 Conflict
 * Content-Type: application/problem+json
 *
 * {
 *   "type": "https://open-elements.com/problems/spring-services/duplicate-contact",
 *   "title": "Duplicate contact",
 *   "status": 409,
 *   "detail": "A contact with this e-mail address already exists",
 *   "instance": "/api/contacts",
 *   "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
 *   "existingContactId": "0b6f3c1e-8d9a-4a57-9a43-1b0d1f3e7c21"
 * }
 * }</pre>
 *
 * <p>The class is not {@code final}, so a domain exception can be one:
 *
 * <pre>{@code
 * public class DuplicateContactException extends ProblemException {
 *   public DuplicateContactException(String email) {
 *     super(CrmProblemType.DUPLICATE_CONTACT, "A contact with " + email + " already exists");
 *   }
 * }
 * }</pre>
 *
 * <h2>What goes into {@code detail}</h2>
 *
 * <p>{@code detail} is written to the client verbatim. It must therefore not carry personal data,
 * SQL, file paths, stack frames or anything else an outsider should not see. The library never adds
 * text of its own to an application problem.
 *
 * <h2>Extension members</h2>
 *
 * <p>{@code properties} become top-level members of the problem, which is how RFC 9457 §3.2 wants
 * machine-readable extras carried. The seven members the library itself writes — {@code type},
 * {@code title}, {@code status}, {@code detail}, {@code instance}, {@code traceId} and {@code
 * errors} — are reserved and rejected by the constructor, because overwriting one would produce a
 * response that contradicts itself.
 *
 * <p>Values are serialised by the application's Jackson mapper.
 */
public class ProblemException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Members written by the library; an extension may not use these names. */
  static final Set<String> RESERVED_MEMBERS =
      Set.of(
          "type",
          "title",
          "status",
          "detail",
          "instance",
          ProblemMembers.TRACE_ID,
          ProblemMembers.ERRORS);

  private final ProblemType type;

  private final @Nullable String detail;

  private final Map<String, Object> properties;

  /**
   * Creates a problem without an occurrence-specific detail.
   *
   * @param type the problem type
   * @throws NullPointerException if {@code type} is {@code null}
   */
  public ProblemException(final ProblemType type) {
    this(type, null, Map.of());
  }

  /**
   * Creates a problem with an occurrence-specific detail.
   *
   * @param type the problem type
   * @param detail human-readable explanation of this occurrence, or {@code null} for none. Written
   *     to the client verbatim — see the class documentation
   * @throws NullPointerException if {@code type} is {@code null}
   */
  public ProblemException(final ProblemType type, final @Nullable String detail) {
    this(type, detail, Map.of());
  }

  /**
   * Creates a problem with an occurrence-specific detail and extension members.
   *
   * @param type the problem type
   * @param detail human-readable explanation of this occurrence, or {@code null} for none
   * @param properties extension members to add to the problem; copied defensively
   * @throws NullPointerException if {@code type} or {@code properties} is {@code null}, or if
   *     {@code properties} contains a {@code null} key or value
   * @throws IllegalArgumentException if {@code properties} uses one of the reserved member names
   *     {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}, {@code
   *     traceId} or {@code errors}
   */
  public ProblemException(
      final ProblemType type, final @Nullable String detail, final Map<String, ?> properties) {
    super(detail != null ? detail : Objects.requireNonNull(type, "type must not be null").title());
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.detail = detail;
    this.properties = copyProperties(properties);
  }

  /**
   * Returns the type of this problem.
   *
   * @return the problem type, never {@code null}
   */
  public ProblemType type() {
    return type;
  }

  /**
   * Returns the occurrence-specific explanation, if there is one.
   *
   * @return the detail, or empty when the problem carries only its type
   */
  public Optional<String> detail() {
    return Optional.ofNullable(detail);
  }

  /**
   * Returns the extension members to add to the problem.
   *
   * @return an immutable map, empty when there are none
   */
  public Map<String, Object> properties() {
    return properties;
  }

  /**
   * Copies and validates the extension members.
   *
   * @param properties the caller's map
   * @return an unmodifiable copy that preserves the caller's iteration order, so the JSON members
   *     come out in a deterministic order
   */
  private static Map<String, Object> copyProperties(final Map<String, ?> properties) {
    Objects.requireNonNull(properties, "properties must not be null");
    final Map<String, Object> copy = new LinkedHashMap<>();
    properties.forEach(
        (key, value) -> {
          Objects.requireNonNull(key, "property name must not be null");
          Objects.requireNonNull(value, "value of property '" + key + "' must not be null");
          if (RESERVED_MEMBERS.contains(key)) {
            throw new IllegalArgumentException(
                "'" + key + "' is written by the library and must not be used as an extension member");
          }
          copy.put(key, value);
        });
    return Collections.unmodifiableMap(copy);
  }
}
