package com.openelements.spring.base.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link TraceParentTraceIdResolver} — the default {@link TraceIdResolver}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two things the rest of the feature depends on:
 *
 * <ol>
 *   <li><b>A valid {@code traceparent} is honoured verbatim.</b> This is what makes a problem's
 *       {@code traceId} match the caller's own trace, and what the GovStack contract test asserts.
 *   <li><b>Every invalid form is rejected and a fresh ID is generated instead.</b> Silently
 *       accepting a malformed header would publish a value that fails the GovStack {@code Problem}
 *       schema's {@code ^(?!0{32}$)[\da-f]{32}$} pattern, and would let a caller choose a trace ID
 *       in a shape the log pipeline does not expect.
 * </ol>
 *
 * <p>The invalid matrix mirrors the "Malformed traceparent is ignored" scenario of {@code
 * behaviors.md} one case per line: uppercase hex, a short trace-id, an all-zero parent-id, version
 * {@code ff}, and version {@code 00} with trailing data. Two {@code traceparent} headers on one
 * request are covered separately, because that case is about header multiplicity rather than the
 * value's syntax.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context. Requests are {@link MockHttpServletRequest} instances, which
 * implement {@code getHeaders(String)} with real multi-value semantics — that is what the duplicate
 * header case needs.
 *
 * <p><b>Mock-Audit.</b> No mocks. {@code MockHttpServletRequest} is Spring's own servlet-API test
 * double, not a behaviour mock.
 */
@DisplayName("TraceParentTraceIdResolver")
class TraceParentTraceIdResolverTest {

  private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String VALID_TRACEPARENT = "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01";

  private final TraceParentTraceIdResolver resolver = new TraceParentTraceIdResolver();

  private static MockHttpServletRequest requestWith(final String... traceparents) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    for (final String value : traceparents) {
      request.addHeader(TraceParentTraceIdResolver.TRACEPARENT_HEADER, value);
    }
    return request;
  }

  @Test
  @DisplayName("A valid traceparent contributes its trace-id unchanged.")
  void validTraceParentIsHonoured() {
    assertThat(resolver.resolve(requestWith(VALID_TRACEPARENT))).isEqualTo(VALID_TRACE_ID);
  }

  @Test
  @DisplayName("A version other than 00 is accepted, and trailing data is tolerated for it.")
  void futureVersionWithTrailingDataIsHonoured() {
    final String traceparent = "01-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01-future";
    assertThat(resolver.resolve(requestWith(traceparent))).isEqualTo(VALID_TRACE_ID);
  }

  @Test
  @DisplayName("Without a traceparent header an ID is generated.")
  void missingHeaderGeneratesId() {
    assertThat(resolver.resolve(requestWith())).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
  }

  @Test
  @DisplayName("An all-zero trace-id is rejected and a fresh, non-zero ID is generated.")
  void allZeroTraceIdIsIgnored() {
    final String traceparent = "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01";
    final String resolved = resolver.resolve(requestWith(traceparent));
    assertThat(resolved).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
        "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-zz",
        "",
        "not-a-traceparent-at-all",
      })
  @DisplayName("Every malformed traceparent is ignored and an ID is generated instead.")
  void malformedTraceParentIsIgnored(final String traceparent) {
    final String resolved = resolver.resolve(requestWith(traceparent));
    assertThat(resolved)
        .matches("[0-9a-f]{32}")
        .isNotEqualTo("0".repeat(32))
        .isNotEqualTo(VALID_TRACE_ID);
  }

  @Test
  @DisplayName("Two traceparent headers are ignored — picking one of them would be arbitrary.")
  void duplicateTraceParentHeadersAreIgnored() {
    final String second = "00-" + "a".repeat(32) + "-00f067aa0ba902b7-01";
    final String resolved = resolver.resolve(requestWith(VALID_TRACEPARENT, second));
    assertThat(resolved).matches("[0-9a-f]{32}").isNotEqualTo(VALID_TRACE_ID).isNotEqualTo("a".repeat(32));
  }

  /**
   * Two consecutive generated IDs must differ. A resolver that returned a constant would still
   * satisfy every shape assertion above while making the trace ID useless for correlation.
   */
  @Test
  @DisplayName("Generated IDs are distinct across requests.")
  void generatedIdsAreDistinct() {
    final long distinct =
        IntStream.range(0, 50).mapToObj(i -> resolver.resolve(requestWith())).distinct().count();
    assertThat(distinct).isEqualTo(50);
  }
}
