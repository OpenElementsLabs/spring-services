package com.openelements.spring.base.trace;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Determines the trace ID of a request.
 *
 * <p>The trace ID correlates a response with the log lines written while the request was handled. It
 * appears as the {@code traceId} member of every RFC 9457 problem produced by {@code
 * com.openelements.spring.base.problem} and in the SLF4J {@link org.slf4j.MDC} under the key {@code
 * traceId} for the duration of the request.
 *
 * <p>The default implementation takes the trace-id field of a valid W3C {@code traceparent} header
 * and generates a random one otherwise. An application replaces it by declaring its own bean of this
 * type — for example to read the ID from a Micrometer {@code Tracer}, or to refuse IDs chosen by an
 * untrusted client.
 *
 * <p><strong>Implementations must return 32 lowercase hexadecimal characters that are not all
 * zero.</strong> That is the trace-id format of the W3C Trace Context specification, and the format
 * the GovStack {@code Problem} schema requires. A value in any other shape makes responses fail
 * schema validation at the consumer.
 *
 * <p>Implementations are called once per request, from a servlet filter, and must be thread-safe.
 */
@FunctionalInterface
public interface TraceIdResolver {

  /**
   * Returns the trace ID for the given request.
   *
   * @param request the request being handled; never {@code null}
   * @return 32 lowercase hexadecimal characters that are not all zero
   */
  String resolve(HttpServletRequest request);
}
