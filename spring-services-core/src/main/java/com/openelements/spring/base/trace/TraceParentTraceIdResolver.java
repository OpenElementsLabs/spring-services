package com.openelements.spring.base.trace;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link TraceIdResolver}: honours a valid incoming {@code traceparent}, generates an ID
 * otherwise.
 *
 * <p>The header is parsed according to <a href="https://www.w3.org/TR/trace-context-1/">W3C Trace
 * Context Level 1</a> §3.2. A header is accepted only if <em>all</em> of the following hold:
 *
 * <ul>
 *   <li>the request carries exactly one {@code traceparent} header — two of them mean a broken or
 *       hostile caller, and picking either one would be arbitrary;
 *   <li>it consists of four {@code -}-separated fields: version, trace-id, parent-id, flags;
 *   <li>the version is two lowercase hex characters and not {@code ff} (the reserved "invalid"
 *       version);
 *   <li>the trace-id is 32 lowercase hex characters and not all zero;
 *   <li>the parent-id is 16 lowercase hex characters and not all zero;
 *   <li>the flags are two lowercase hex characters;
 *   <li>for version {@code 00} there is no trailing data. A future version may append fields, so
 *       trailing data is tolerated for versions other than {@code 00} — that is what the
 *       specification requires of a Level 1 implementation.
 * </ul>
 *
 * <p>Uppercase hexadecimal is rejected rather than lowercased: the specification mandates lowercase,
 * and silently accepting another spelling would make two callers with the same logical ID produce
 * two different {@code traceId} values.
 *
 * <p>When the header is absent or rejected, the ID is generated from 16 bytes of {@link
 * SecureRandom} and rendered as lowercase hex. The (vanishingly unlikely) all-zero draw is rejected
 * and redrawn, because an all-zero trace-id is invalid.
 *
 * <p>Accepting a client-supplied trace ID is a deliberate, documented choice: it makes end-to-end
 * correlation work out of the box. It does not permit log injection — the value is validated as hex
 * before it is used anywhere. An untrusted client can, however, pick its own ID and so make
 * correlation unreliable; a switch for that is deferred to the tracing spec (see {@code
 * docs/TODO.md}).
 */
final class TraceParentTraceIdResolver implements TraceIdResolver {

  /** The W3C header carrying the incoming trace context. */
  static final String TRACEPARENT_HEADER = "traceparent";

  private static final Pattern VERSION = Pattern.compile("[0-9a-f]{2}");
  private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
  private static final Pattern PARENT_ID = Pattern.compile("[0-9a-f]{16}");
  private static final Pattern FLAGS = Pattern.compile("[0-9a-f]{2}");

  private static final String INVALID_VERSION = "ff";
  private static final String SUPPORTED_VERSION = "00";
  private static final String ZERO_TRACE_ID = "0".repeat(32);
  private static final String ZERO_PARENT_ID = "0".repeat(16);

  private static final int TRACE_ID_BYTES = 16;

  private final SecureRandom random = new SecureRandom();

  TraceParentTraceIdResolver() {}

  @Override
  public String resolve(final HttpServletRequest request) {
    final String incoming = traceIdFromTraceParent(request);
    return incoming != null ? incoming : generate();
  }

  /**
   * Extracts the trace-id field of the request's {@code traceparent} header.
   *
   * @param request the request to read the header from
   * @return the 32-hex trace-id, or {@code null} if the header is absent, duplicated or malformed
   */
  private static @Nullable String traceIdFromTraceParent(final HttpServletRequest request) {
    final List<String> headers = Collections.list(request.getHeaders(TRACEPARENT_HEADER));
    if (headers.size() != 1) {
      return null;
    }
    return parse(headers.get(0));
  }

  /**
   * Parses one {@code traceparent} header value.
   *
   * @param value the raw header value
   * @return the trace-id field if the value is a valid {@code traceparent}, {@code null} otherwise
   */
  static @Nullable String parse(final @Nullable String value) {
    if (value == null) {
      return null;
    }
    final String[] fields = value.split("-", 5);
    if (fields.length < 4) {
      return null;
    }
    final String version = fields[0];
    final String traceId = fields[1];
    final String parentId = fields[2];
    final String flags = fields[3];

    if (!VERSION.matcher(version).matches() || INVALID_VERSION.equals(version)) {
      return null;
    }
    if (SUPPORTED_VERSION.equals(version) && fields.length > 4) {
      return null;
    }
    if (!TRACE_ID.matcher(traceId).matches() || ZERO_TRACE_ID.equals(traceId)) {
      return null;
    }
    if (!PARENT_ID.matcher(parentId).matches() || ZERO_PARENT_ID.equals(parentId)) {
      return null;
    }
    if (!FLAGS.matcher(flags).matches()) {
      return null;
    }
    return traceId;
  }

  /**
   * Draws a fresh trace ID.
   *
   * @return 32 lowercase hex characters that are not all zero
   */
  private String generate() {
    final byte[] bytes = new byte[TRACE_ID_BYTES];
    String candidate;
    do {
      random.nextBytes(bytes);
      candidate = HexFormat.of().formatHex(bytes);
    } while (ZERO_TRACE_ID.equals(candidate));
    return candidate;
  }
}
