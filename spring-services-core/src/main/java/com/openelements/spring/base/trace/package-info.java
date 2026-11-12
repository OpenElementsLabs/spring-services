/**
 * The trace ID of a request.
 *
 * <p>A trace ID ties a response to the log lines written while the request was handled. This package
 * contributes the minimum needed for that: resolve one ID per request, publish it as a request
 * attribute and in the SLF4J {@link org.slf4j.MDC} under {@code traceId}, and let anything that
 * writes a response read it back through {@link com.openelements.spring.base.trace.TraceContext}.
 *
 * <p>The ID ends up in the {@code traceId} member of every RFC 9457 problem produced by {@link
 * com.openelements.spring.base.problem}. The two packages are otherwise independent: the trace
 * filter runs even when {@code openelements.problem-details.enabled} is {@code false}.
 *
 * <h2>Where the ID comes from</h2>
 *
 * <p>{@link com.openelements.spring.base.trace.TraceIdResolver} decides. The default takes the
 * trace-id field of a valid W3C {@code traceparent} header, so a caller that already traces its own
 * request sees the same ID on both sides, and generates a random one otherwise. An application
 * replaces the default by declaring its own {@code TraceIdResolver} bean.
 *
 * <h2>Seeing it in the log</h2>
 *
 * <p>Add {@code %X{traceId}} to the log pattern:
 *
 * <pre>{@code
 * logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-}]
 * }</pre>
 *
 * <h2>What is deliberately missing</h2>
 *
 * <p>No {@code traceresponse} header, no span IDs, no sampling, no Micrometer Tracing bridge and no
 * switch for refusing a client-chosen ID. This is a correlation handle for error responses, not a
 * tracing implementation; the full feature is a spec of its own (see {@code docs/TODO.md}).
 */
package com.openelements.spring.base.trace;
