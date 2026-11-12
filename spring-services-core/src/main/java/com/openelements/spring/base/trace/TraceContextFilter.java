package com.openelements.spring.base.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Publishes the request's trace ID as a request attribute and in the SLF4J {@link MDC}.
 *
 * <p>Registered with an order just ahead of Spring Security's filter chain, so a {@code 401} written
 * by an authentication entry point already carries the ID, and so does every log line produced while
 * the request is being authenticated.
 *
 * <p>The filter runs on {@code ERROR} dispatches as well, because that is where container errors and
 * {@code sendError(...)} from a filter are rendered. It does not re-resolve there: the attribute
 * survives the dispatch, so the error response reports the same ID as the request that produced it.
 * The MDC, in contrast, is thread-local and is restored for the dispatch.
 *
 * <p>The MDC entry is removed in a {@code finally} block. Servlet containers pool threads, so a
 * leaked entry would attach a stale ID to an unrelated request.
 */
final class TraceContextFilter extends OncePerRequestFilter {

  private final TraceIdResolver resolver;

  TraceContextFilter(final TraceIdResolver resolver) {
    this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final String traceId = resolveOnce(request);
    MDC.put(TraceContext.MDC_KEY, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(TraceContext.MDC_KEY);
    }
  }

  /**
   * Returns the ID already attached to this request, resolving and attaching one on first sight.
   *
   * @param request the request being handled
   * @return the trace ID for this request
   */
  private String resolveOnce(final HttpServletRequest request) {
    final Object stored = request.getAttribute(TraceContext.TRACE_ID_ATTRIBUTE);
    if (stored instanceof String traceId) {
      return traceId;
    }
    final String traceId = resolver.resolve(request);
    request.setAttribute(TraceContext.TRACE_ID_ATTRIBUTE, traceId);
    return traceId;
  }

  /**
   * Runs on {@code ERROR} dispatches too — {@link OncePerRequestFilter} skips them by default, but
   * the error response is exactly where the trace ID has to be available.
   */
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  /** Runs on asynchronous dispatches too, so the MDC is populated on the dispatch thread. */
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }
}
