package com.openelements.spring.base.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * The trace filter over HTTP.
 *
 * <h2>What is tested</h2>
 *
 * <p>The three guarantees the rest of the library builds on, none of which a unit test on the
 * resolver can show:
 *
 * <ol>
 *   <li><b>One ID per request.</b> What a handler reads from {@link TraceContext} and what the log
 *       MDC carries are the same value — that is the whole point of correlating a response with a
 *       log line.
 *   <li><b>The MDC is cleaned up on the request thread.</b> Servlet containers pool threads, so a
 *       leaked entry would silently attach one request's trace ID to a later, unrelated request. The
 *       assertion runs <em>inside</em> the handler, after the filter's {@code finally} has had its
 *       chance on a second request, rather than on the test thread where it would prove nothing.
 *   <li><b>An application {@link TraceIdResolver} really is used</b>, not just registered.
 * </ol>
 *
 * <p>The malformed-{@code traceparent} matrix is repeated here, over HTTP, because the resolver's
 * unit test drives {@code getHeaders(...)} directly while this one goes through the servlet stack.
 *
 * <p>The counterpart — the filter still running with {@code
 * openelements.problem-details.enabled=false} — lives in {@code ProblemDetailsDisabledWebTest},
 * next to the other assertions about that switch.
 *
 * <h2>How it is tested</h2>
 *
 * <p>A {@link WebApplicationContextRunner} with the real auto-configuration, and the filter taken
 * out of its {@link FilterRegistrationBean} and handed to {@link MockMvc} — MockMvc runs only the
 * filters it is given.
 *
 * <p><b>Mock-Audit.</b> No mocks.
 */
@DisplayName("Trace context over HTTP")
class TraceContextFilterWebTest {

  private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String VALID_TRACEPARENT = "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01";

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TraceAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  WebMvcAutoConfiguration.class))
          .withUserConfiguration(TraceProbe.Config.class);

  private ListAppender<ILoggingEvent> appender;
  private Logger probeLog;

  @BeforeEach
  void attachAppender() {
    probeLog = (Logger) LoggerFactory.getLogger(TraceProbe.class);
    appender = new ListAppender<>();
    appender.start();
    probeLog.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    probeLog.detachAppender(appender);
  }

  private void run(final WebApplicationContextRunner custom, final MvcAssertion assertion) {
    custom.run(
        context -> {
          final Filter traceFilter =
              context
                  .getBean("traceContextFilterRegistration", FilterRegistrationBean.class)
                  .getFilter();
          assertion.accept(
              MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                  .addFilters(traceFilter)
                  .build());
        });
  }

  private void run(final MvcAssertion assertion) {
    run(runner, assertion);
  }

  /** Performs a request to the probe and returns the trace ID it reported. */
  private static String traceIdOf(final MockMvc mvc, final String... traceparents) throws Exception {
    var request = get("/api/trace");
    for (final String traceparent : traceparents) {
      request = request.header(TraceParentTraceIdResolver.TRACEPARENT_HEADER, traceparent);
    }
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @FunctionalInterface
  private interface MvcAssertion {
    void accept(MockMvc mvc) throws Exception;
  }

  @Test
  @DisplayName("A valid traceparent is honoured and reaches both the handler and the MDC")
  void validTraceParentIsHonoured() {
    run(
        mvc -> {
          assertThat(traceIdOf(mvc, VALID_TRACEPARENT)).isEqualTo(VALID_TRACE_ID);
          assertThat(appender.list)
              .singleElement()
              .satisfies(
                  event ->
                      assertThat(event.getMDCPropertyMap())
                          .containsEntry(TraceContext.MDC_KEY, VALID_TRACE_ID));
        });
  }

  @Test
  @DisplayName("Without a traceparent a fresh, non-zero ID is generated per request")
  void withoutTraceParentAnIdIsGenerated() {
    run(
        mvc -> {
          final String first = traceIdOf(mvc);
          final String second = traceIdOf(mvc);

          assertThat(first).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
          assertThat(second).isNotEqualTo(first);
        });
  }

  @Test
  @DisplayName("An all-zero trace-id is ignored")
  void allZeroTraceIdIsIgnored() {
    run(
        mvc ->
            assertThat(traceIdOf(mvc, "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01"))
                .matches("[0-9a-f]{32}")
                .isNotEqualTo("0".repeat(32)));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
        "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra",
      })
  @DisplayName("A malformed traceparent is ignored over HTTP too")
  void malformedTraceParentIsIgnored(final String traceparent) {
    run(
        mvc ->
            assertThat(traceIdOf(mvc, traceparent))
                .matches("[0-9a-f]{32}")
                .isNotEqualTo(VALID_TRACE_ID));
  }

  @Test
  @DisplayName("Two traceparent headers are ignored")
  void duplicateTraceParentHeadersAreIgnored() {
    run(
        mvc ->
            assertThat(traceIdOf(mvc, VALID_TRACEPARENT, "00-" + "a".repeat(32) + "-00f067aa0ba902b7-01"))
                .isNotEqualTo(VALID_TRACE_ID)
                .isNotEqualTo("a".repeat(32)));
  }

  /**
   * The probe reports what the MDC held when the handler started. On the second request that value
   * must be the second request's own ID — if the filter's {@code finally} had failed to clean up, a
   * pooled thread would still carry the first one.
   */
  @Test
  @DisplayName("The MDC does not leak from one request to the next on the same thread")
  void mdcDoesNotLeakBetweenRequests() {
    run(
        mvc -> {
          final String first = traceIdOf(mvc, VALID_TRACEPARENT);
          final String second = traceIdOf(mvc);

          assertThat(first).isEqualTo(VALID_TRACE_ID);
          assertThat(second).isNotEqualTo(VALID_TRACE_ID);
          assertThat(appender.list)
              .extracting(event -> event.getMDCPropertyMap().get(TraceContext.MDC_KEY))
              .containsExactly(first, second);
        });
  }

  @Test
  @DisplayName("After the request the MDC is clean on the calling thread")
  void mdcIsCleanAfterTheRequest() {
    run(
        mvc -> {
          traceIdOf(mvc, VALID_TRACEPARENT);
          assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();
        });
  }

  @Test
  @DisplayName("An application TraceIdResolver is the one that runs")
  void applicationResolverIsUsed() {
    run(
        runner.withUserConfiguration(FixedResolver.class),
        mvc -> {
          assertThat(traceIdOf(mvc, VALID_TRACEPARENT)).isEqualTo(FixedResolver.FIXED_ID);
          assertThat(appender.list)
              .singleElement()
              .satisfies(
                  event ->
                      assertThat(event.getMDCPropertyMap())
                          .containsEntry(TraceContext.MDC_KEY, FixedResolver.FIXED_ID));
        });
  }

  /** Reports the trace ID of the request and logs one line, so the MDC can be inspected. */
  @RestController
  static class TraceProbe {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(TraceProbe.class);

    @GetMapping(value = "/api/trace", produces = MediaType.TEXT_PLAIN_VALUE)
    String trace(final HttpServletRequest request) {
      LOG.info("handling a request");
      return TraceContext.traceId(request);
    }

    /** Registers the probe. */
    @Configuration(proxyBeanMethods = false)
    static class Config {

      @Bean
      TraceProbe traceProbe() {
        return new TraceProbe();
      }
    }
  }

  /** An application resolver that ignores the request entirely. */
  @Configuration(proxyBeanMethods = false)
  static class FixedResolver {

    static final String FIXED_ID = "aaaaaaaabbbbbbbbccccccccdddddddd";

    @Bean
    TraceIdResolver applicationTraceIdResolver() {
      return request -> FIXED_ID;
    }
  }
}
