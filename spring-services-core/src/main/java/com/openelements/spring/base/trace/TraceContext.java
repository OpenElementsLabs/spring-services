package com.openelements.spring.base.trace;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * Access to the trace ID of the request being handled.
 *
 * <p>{@code TraceContextFilter} resolves the ID once per request and stores it as a request
 * attribute; this class reads it back. Everything that writes a problem — the exception handlers,
 * the error controller and the security handlers — goes through here, so a single request always
 * reports a single ID, however many of those take part in it.
 *
 * <p>If the attribute is missing the ID is resolved on the spot and stored, so the value stays
 * stable for the rest of the request. That happens when the filter did not run: a standalone {@code
 * MockMvc} setup, or a servlet container that dispatches outside the filter chain. The fallback
 * applies the same rules as the default resolver, but it does <strong>not</strong> use an
 * application-supplied {@link TraceIdResolver} bean — that one is only reachable through the filter.
 *
 * <p>The MDC key is {@code traceId}; add {@code %X{traceId}} to a log pattern to see it.
 */
public final class TraceContext {

  /** Request attribute under which the resolved trace ID is stored. */
  static final String TRACE_ID_ATTRIBUTE = TraceContext.class.getName() + ".traceId";

  /** MDC key under which the trace ID is published for the duration of the request. */
  public static final String MDC_KEY = "traceId";

  /**
   * Used only when the filter did not run. Stateless apart from its {@link
   * java.security.SecureRandom}, and therefore safe to share.
   */
  private static final TraceIdResolver FALLBACK = new TraceParentTraceIdResolver();

  private TraceContext() {
    throw new AssertionError("TraceContext must not be instantiated");
  }

  /**
   * Returns the trace ID of the given request.
   *
   * @param request the request being handled
   * @return 32 lowercase hexadecimal characters that are not all zero
   * @throws NullPointerException if {@code request} is {@code null}
   */
  public static String traceId(final HttpServletRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    final Object stored = request.getAttribute(TRACE_ID_ATTRIBUTE);
    if (stored instanceof String traceId) {
      return traceId;
    }
    final String resolved = FALLBACK.resolve(request);
    request.setAttribute(TRACE_ID_ATTRIBUTE, resolved);
    return resolved;
  }
}
