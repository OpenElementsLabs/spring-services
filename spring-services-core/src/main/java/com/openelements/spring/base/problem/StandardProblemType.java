package com.openelements.spring.base.problem;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * The problem types shipped by {@code spring-services}.
 *
 * <p>One constant per HTTP error status defined by RFC 9110, plus the registered extension statuses
 * (423, 424, 425, 428, 429, 431, 451, 507, 508, 511), plus four types that describe a cause rather
 * than a status code: {@link #VALIDATION_FAILED}, {@link #MALFORMED_REQUEST}, {@link
 * #INVALID_PARAMETER} — all three {@code 400} — and {@link #INTERNAL_ERROR}, whose slug is {@code
 * internal-error} rather than the longer RFC name.
 *
 * <h2>Why the slugs are literals</h2>
 *
 * <p>Every slug below is written out by hand instead of being derived from {@link HttpStatus} at
 * runtime. Clients compare the full {@code type} URI, so a slug is as binding as a REST path — and
 * Spring's constant names are not stable enough to carry one: Spring Framework 6 renamed {@code
 * PAYLOAD_TOO_LARGE} to {@code CONTENT_TOO_LARGE} and {@code UNPROCESSABLE_ENTITY} to {@code
 * UNPROCESSABLE_CONTENT}. Deriving the slug would have silently changed two published type URIs on a
 * Spring upgrade. The names here follow RFC 9110, not Spring.
 *
 * <p>Two RFC 9110 statuses are deliberately absent. {@code 418} is listed as unused by §15.5.19.
 * {@code 421 Misdirected Request} has no {@link HttpStatus} constant in Spring Framework 6 — it is a
 * connection-level status produced by a reverse proxy, not by a request handler — and this enum does
 * not invent one. Both resolve through {@link #forStatus(int)} to a synthesised {@code http-<code>}
 * type, like any other status without a name.
 */
public enum StandardProblemType implements ProblemType {

  /** {@code 400} — the request is not valid for reasons the other 400 types do not describe. */
  BAD_REQUEST("bad-request", HttpStatus.BAD_REQUEST, "Bad Request"),
  /** {@code 401} — authentication is missing or failed. */
  UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "Unauthorized"),
  /** {@code 402} — reserved for future use. */
  PAYMENT_REQUIRED("payment-required", HttpStatus.PAYMENT_REQUIRED, "Payment Required"),
  /** {@code 403} — the caller is authenticated but not allowed to do this. */
  FORBIDDEN("forbidden", HttpStatus.FORBIDDEN, "Forbidden"),
  /** {@code 404} — no resource at this path. */
  NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Not Found"),
  /** {@code 405} — the path exists but not for this method. */
  METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed"),
  /** {@code 406} — no representation matches the {@code Accept} header. */
  NOT_ACCEPTABLE("not-acceptable", HttpStatus.NOT_ACCEPTABLE, "Not Acceptable"),
  /** {@code 407} — the proxy requires authentication. */
  PROXY_AUTHENTICATION_REQUIRED(
      "proxy-authentication-required",
      HttpStatus.PROXY_AUTHENTICATION_REQUIRED,
      "Proxy Authentication Required"),
  /** {@code 408} — the client did not produce a request in time. */
  REQUEST_TIMEOUT("request-timeout", HttpStatus.REQUEST_TIMEOUT, "Request Timeout"),
  /** {@code 409} — the request conflicts with the current state of the resource. */
  CONFLICT("conflict", HttpStatus.CONFLICT, "Conflict"),
  /** {@code 410} — the resource is permanently gone. */
  GONE("gone", HttpStatus.GONE, "Gone"),
  /** {@code 411} — a {@code Content-Length} header is required. */
  LENGTH_REQUIRED("length-required", HttpStatus.LENGTH_REQUIRED, "Length Required"),
  /** {@code 412} — a precondition in the request headers failed. */
  PRECONDITION_FAILED("precondition-failed", HttpStatus.PRECONDITION_FAILED, "Precondition Failed"),
  /** {@code 413} — the request body is larger than the server accepts. */
  CONTENT_TOO_LARGE("content-too-large", HttpStatus.PAYLOAD_TOO_LARGE, "Content Too Large"),
  /** {@code 414} — the request target is longer than the server accepts. */
  URI_TOO_LONG("uri-too-long", HttpStatus.URI_TOO_LONG, "URI Too Long"),
  /** {@code 415} — the body's media type is not supported. */
  UNSUPPORTED_MEDIA_TYPE(
      "unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type"),
  /** {@code 416} — the requested range cannot be served. */
  RANGE_NOT_SATISFIABLE(
      "range-not-satisfiable", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Range Not Satisfiable"),
  /** {@code 417} — the {@code Expect} header cannot be met. */
  EXPECTATION_FAILED("expectation-failed", HttpStatus.EXPECTATION_FAILED, "Expectation Failed"),
  /** {@code 422} — the request is well-formed but semantically wrong. */
  UNPROCESSABLE_CONTENT(
      "unprocessable-content", HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Content"),
  /** {@code 423} — the resource is locked (RFC 4918). */
  LOCKED("locked", HttpStatus.LOCKED, "Locked"),
  /** {@code 424} — the request failed because a dependent request failed (RFC 4918). */
  FAILED_DEPENDENCY("failed-dependency", HttpStatus.FAILED_DEPENDENCY, "Failed Dependency"),
  /** {@code 425} — the server refuses to risk processing a replayed request (RFC 8470). */
  TOO_EARLY("too-early", HttpStatus.TOO_EARLY, "Too Early"),
  /** {@code 426} — the client must switch to a different protocol. */
  UPGRADE_REQUIRED("upgrade-required", HttpStatus.UPGRADE_REQUIRED, "Upgrade Required"),
  /** {@code 428} — the request must be conditional (RFC 6585). */
  PRECONDITION_REQUIRED(
      "precondition-required", HttpStatus.PRECONDITION_REQUIRED, "Precondition Required"),
  /** {@code 429} — the caller sent too many requests (RFC 6585). */
  TOO_MANY_REQUESTS("too-many-requests", HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"),
  /** {@code 431} — the request headers are too large (RFC 6585). */
  REQUEST_HEADER_FIELDS_TOO_LARGE(
      "request-header-fields-too-large",
      HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE,
      "Request Header Fields Too Large"),
  /** {@code 451} — the resource is withheld for legal reasons (RFC 7725). */
  UNAVAILABLE_FOR_LEGAL_REASONS(
      "unavailable-for-legal-reasons",
      HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS,
      "Unavailable For Legal Reasons"),

  /** {@code 500} — an unexpected failure. The slug is shortened; the title follows RFC 9110. */
  INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"),
  /** {@code 501} — the server does not support the functionality required. */
  NOT_IMPLEMENTED("not-implemented", HttpStatus.NOT_IMPLEMENTED, "Not Implemented"),
  /** {@code 502} — an upstream server returned an invalid response. */
  BAD_GATEWAY("bad-gateway", HttpStatus.BAD_GATEWAY, "Bad Gateway"),
  /** {@code 503} — the server is temporarily unable to handle the request. */
  SERVICE_UNAVAILABLE(
      "service-unavailable", HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"),
  /** {@code 504} — an upstream server did not respond in time. */
  GATEWAY_TIMEOUT("gateway-timeout", HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout"),
  /** {@code 505} — the HTTP version is not supported. */
  HTTP_VERSION_NOT_SUPPORTED(
      "http-version-not-supported",
      HttpStatus.HTTP_VERSION_NOT_SUPPORTED,
      "HTTP Version Not Supported"),
  /** {@code 507} — the server cannot store the representation (RFC 4918). */
  INSUFFICIENT_STORAGE("insufficient-storage", HttpStatus.INSUFFICIENT_STORAGE, "Insufficient Storage"),
  /** {@code 508} — an infinite loop was detected (RFC 5842). */
  LOOP_DETECTED("loop-detected", HttpStatus.LOOP_DETECTED, "Loop Detected"),
  /** {@code 511} — the client must authenticate to gain network access (RFC 6585). */
  NETWORK_AUTHENTICATION_REQUIRED(
      "network-authentication-required",
      HttpStatus.NETWORK_AUTHENTICATION_REQUIRED,
      "Network Authentication Required"),

  /** {@code 400} — one or more fields of the request body failed validation. Carries {@code errors}. */
  VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST, "Validation failed"),
  /** {@code 400} — the request body could not be parsed at all. */
  MALFORMED_REQUEST("malformed-request", HttpStatus.BAD_REQUEST, "Malformed request"),
  /** {@code 400} — a query, path or header parameter is missing or unusable. */
  INVALID_PARAMETER("invalid-parameter", HttpStatus.BAD_REQUEST, "Invalid parameter");

  /**
   * Status code to the constant that <em>names</em> that status. The four cause-describing types are
   * deliberately absent, so {@code forStatus(400)} is {@link #BAD_REQUEST} and never {@link
   * #VALIDATION_FAILED}.
   */
  private static final Map<Integer, StandardProblemType> BY_STATUS = byStatus();

  private final String slug;
  private final HttpStatus status;
  private final String title;

  StandardProblemType(final String slug, final HttpStatus status, final String title) {
    this.slug = slug;
    this.status = status;
    this.title = title;
  }

  @Override
  public String slug() {
    return slug;
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String title() {
    return title;
  }

  /**
   * Returns the problem type that names the given HTTP status.
   *
   * <p>{@code 400} yields {@link #BAD_REQUEST}, {@code 500} yields {@link #INTERNAL_ERROR}. A status
   * without a constant — {@code 418}, or a code outside the registry — yields a synthesised type with
   * the slug {@code http-<code>} and the title {@code HTTP <code>}, so a response never loses its
   * {@code type} member just because the status is unusual.
   *
   * <p>For a code that {@link HttpStatus} itself does not know, the synthesised type reports {@link
   * HttpStatus#INTERNAL_SERVER_ERROR} from {@link #status()}. Callers that know the real code — the
   * error controller does — set it on the {@code ProblemDetail} afterwards, so the body always agrees
   * with the status line.
   *
   * @param statusCode the HTTP status code of the response
   * @return the matching problem type, never {@code null}
   */
  public static ProblemType forStatus(final int statusCode) {
    final StandardProblemType known = BY_STATUS.get(statusCode);
    if (known != null) {
      return known;
    }
    final HttpStatus resolved = HttpStatus.resolve(statusCode);
    final HttpStatus status =
        resolved != null && resolved.isError() ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    return ProblemType.of("http-" + statusCode, status, "HTTP " + statusCode);
  }

  /**
   * Builds the status lookup, keeping only the first constant declared for a status so the
   * cause-describing {@code 400} types never shadow {@link #BAD_REQUEST}.
   *
   * @return an immutable status-to-type map
   */
  private static Map<Integer, StandardProblemType> byStatus() {
    final Map<Integer, StandardProblemType> map = new HashMap<>();
    for (final StandardProblemType type : values()) {
      map.putIfAbsent(type.status.value(), type);
    }
    return Map.copyOf(map);
  }
}
