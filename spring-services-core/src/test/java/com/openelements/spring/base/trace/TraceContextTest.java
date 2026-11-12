package com.openelements.spring.base.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link TraceContext} — the read side of the trace ID.
 *
 * <h2>What is tested</h2>
 *
 * <p>The two properties every problem writer relies on:
 *
 * <ol>
 *   <li><b>Stability.</b> Repeated reads of the same request yield the same ID, whether the filter
 *       stored one or the fallback had to resolve it. Without this, the ID logged with a {@code 500}
 *       and the ID in the response body could differ, which is precisely what the feature exists to
 *       prevent.
 *   <li><b>Fallback semantics.</b> With no attribute present the context applies the default rules —
 *       honour a valid {@code traceparent}, generate otherwise — and <em>stores</em> the result, so
 *       the first read fixes the value for the rest of the request.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 against {@link MockHttpServletRequest}; no Spring context and no mocks.
 */
@DisplayName("TraceContext")
class TraceContextTest {

  private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Test
  @DisplayName("A stored attribute is returned unchanged.")
  void storedAttributeIsReturned() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(TraceContext.TRACE_ID_ATTRIBUTE, VALID_TRACE_ID);
    assertThat(TraceContext.traceId(request)).isEqualTo(VALID_TRACE_ID);
  }

  @Test
  @DisplayName("Without the attribute the ID is resolved from a valid traceparent and stored.")
  void fallbackHonoursTraceParentAndStores() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        TraceParentTraceIdResolver.TRACEPARENT_HEADER,
        "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01");

    assertThat(TraceContext.traceId(request)).isEqualTo(VALID_TRACE_ID);
    assertThat(request.getAttribute(TraceContext.TRACE_ID_ATTRIBUTE)).isEqualTo(VALID_TRACE_ID);
  }

  @Test
  @DisplayName("Without the attribute and without a traceparent, the generated ID is stable across reads.")
  void fallbackGeneratesStableId() {
    final MockHttpServletRequest request = new MockHttpServletRequest();

    final String first = TraceContext.traceId(request);
    final String second = TraceContext.traceId(request);

    assertThat(first).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("A null request is rejected with NullPointerException.")
  void nullRequestRejected() {
    assertThatThrownBy(() -> TraceContext.traceId(null)).isInstanceOf(NullPointerException.class);
  }
}
