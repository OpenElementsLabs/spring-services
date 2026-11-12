package com.openelements.spring.base.problem;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the RFC 9457 error responses, bound from {@code openelements.problem-details.*}.
 *
 * <pre>{@code
 * openelements.problem-details.enabled=true
 * openelements.problem-details.type-base=https://open-elements.com/problems/spring-services/
 * openelements.problem-details.include-instance=true
 * }</pre>
 *
 * @param enabled whether error responses are RFC 9457 problems. Defaults to {@code true}. Setting it
 *     to {@code false} restores the behaviour from before this feature: a JSON {@code 401} with
 *     {@code status}/{@code error}/{@code message}, Spring Boot's {@code BasicErrorController}, and
 *     ALTCHA's {@code {code, message}} body. The trace filter keeps running either way
 * @param typeBase the base URI every {@code type} member is built from, by appending the problem
 *     type's slug. Must be an absolute URI whose path ends with {@code /}. Applies to the library's
 *     types and the application's alike — an application that publishes its own problem
 *     documentation sets its own base and the library's types move under it. Defaults to
 *     {@code https://open-elements.com/problems/spring-services/}
 * @param includeInstance whether the problem carries an {@code instance} member with the request
 *     path. The query string is never included, with or without this flag, because it may carry
 *     personal data. Defaults to {@code true}
 */
@ConfigurationProperties("openelements.problem-details")
public record ProblemDetailsProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("https://open-elements.com/problems/spring-services/") String typeBase,
    @DefaultValue("true") boolean includeInstance) {

  /** Named in every validation failure so the operator knows which property to fix. */
  private static final String TYPE_BASE_PROPERTY = "openelements.problem-details.type-base";

  /**
   * Validates {@link #typeBase} at binding time, so a misconfigured base fails start-up instead of
   * producing {@code type} URIs no client can resolve.
   */
  public ProblemDetailsProperties {
    Objects.requireNonNull(typeBase, TYPE_BASE_PROPERTY + " must not be null");
    final URI parsed = parse(typeBase);
    if (!parsed.isAbsolute()) {
      throw new IllegalArgumentException(
          TYPE_BASE_PROPERTY + " must be an absolute URI but was '" + typeBase + "'");
    }
    if (!typeBase.endsWith("/")) {
      throw new IllegalArgumentException(
          TYPE_BASE_PROPERTY
              + " must end with '/' so the problem slug can be appended, but was '"
              + typeBase
              + "'");
    }
  }

  /**
   * Builds the {@code type} URI of a problem type.
   *
   * @param type the problem type
   * @return {@link #typeBase} with the type's slug appended
   */
  URI typeUri(final ProblemType type) {
    return URI.create(typeBase + type.slug());
  }

  /**
   * Parses the configured base, turning a syntax error into the same message as the other checks.
   *
   * @param typeBase the configured value
   * @return the parsed URI
   */
  private static URI parse(final String typeBase) {
    try {
      return new URI(typeBase);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException(
          TYPE_BASE_PROPERTY + " must be a valid URI but was '" + typeBase + "'", e);
    }
  }
}
