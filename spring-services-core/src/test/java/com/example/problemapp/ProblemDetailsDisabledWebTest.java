package com.example.problemapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.problem.ProblemDetailsExceptionHandler;
import com.openelements.spring.base.problem.ProblemFactory;
import com.openelements.spring.base.problem.ProblemResponseWriter;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The escape hatch: {@code openelements.problem-details.enabled=false}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That turning the feature off is a complete rollback, not a partial one. An application that
 * cannot migrate its clients sets one property, and the library has to stop contributing error
 * handling altogether: no factory, no advice, no catch-all resolver, no error controller — and Spring
 * Boot's {@code BasicErrorController} back in its place.
 *
 * <p>A half-disabled feature is the dangerous outcome here. If, say, the catch-all resolver kept
 * running, an application would get Boot's format for some errors and RFC 9457 for others, with no
 * symptom until a client hit the wrong one.
 *
 * <p>The trace filter is the deliberate exception. It is request infrastructure, not part of the
 * error format, so {@code traceId} stays in the log MDC either way — asserted here because a
 * regression would be invisible in a test that only looked at response bodies.
 *
 * <h2>How it is tested</h2>
 *
 * <p>The same {@link WebApplicationContextRunner} set-up as {@link ProblemDetailsWebTest}, including
 * {@link ProblemDetailsAutoConfiguration}: the property, not the absence of the auto-configuration,
 * has to do the disabling.
 */
@DisplayName("Problem details disabled")
class ProblemDetailsDisabledWebTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ProblemDetailsAutoConfiguration.class,
                  TraceAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  ValidationAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  ErrorMvcAutoConfiguration.class))
          .withUserConfiguration(ProblemTestApp.class)
          .withPropertyValues("openelements.problem-details.enabled=false");

  @Test
  @DisplayName("No bean of the feature is registered and Boot's error controller is back")
  void noLibraryErrorHandlingIsRegistered() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ProblemFactory.class);
          assertThat(context).doesNotHaveBean(ProblemResponseWriter.class);
          assertThat(context).doesNotHaveBean(ProblemDetailsExceptionHandler.class);
          assertThat(context.getBean(ErrorController.class).getClass().getSimpleName())
              .isEqualTo("BasicErrorController");
        });
  }

  /**
   * Without the library's resolver, a {@code ResponseStatusException} falls through to Spring's
   * {@code ResponseStatusExceptionResolver}, which calls {@code sendError(404)} and leaves the body
   * to the container — exactly what this library produced before the feature. The assertion is
   * therefore about what the response is <em>not</em>: it carries nothing of RFC 9457.
   */
  @Test
  @DisplayName("An error response carries nothing of RFC 9457")
  void errorsAreNotProblems() {
    runner.run(
        context -> {
          final MockMvc mvc =
              MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();

          final var response =
              mvc.perform(get("/api/test/fail"))
                  .andExpect(status().isNotFound())
                  .andReturn()
                  .getResponse();

          // sendError(404) leaves no content type at all, which is itself "not a problem".
          assertThat(String.valueOf(response.getContentType())).doesNotContain("problem+json");
          assertThat(response.getContentAsString())
              .doesNotContain("open-elements.com/problems")
              .doesNotContain("traceId");
        });
  }

  @Test
  @DisplayName("An unexpected exception is not turned into an internal-error problem")
  void unexpectedExceptionIsNotAProblem() {
    runner.run(
        context -> {
          final MockMvc mvc =
              MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();

          assertThat(
                  org.assertj.core.api.Assertions.catchThrowable(
                      () -> mvc.perform(get("/api/test/boom"))))
              .as("without the catch-all the exception propagates, as it did before the feature")
              .isNotNull();
        });
  }

  /**
   * The trace filter is independent of the error format on purpose. An application that keeps the
   * old bodies still gets log correlation, which is the half of the feature that costs it nothing.
   */
  @Test
  @DisplayName("The trace filter still populates the MDC")
  void traceFilterStillRuns() {
    final Logger controllerLog = (Logger) LoggerFactory.getLogger(MdcProbe.class);
    final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    controllerLog.addAppender(appender);
    try {
      runner
          .withUserConfiguration(MdcProbe.Config.class)
          .run(
              context -> {
                final Filter traceFilter =
                    context
                        .getBean("traceContextFilterRegistration", FilterRegistrationBean.class)
                        .getFilter();
                final MockMvc mvc =
                    MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                        .addFilters(traceFilter)
                        .build();

                mvc.perform(
                        get("/api/test/mdc")
                            .header(
                                "traceparent",
                                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                    .andExpect(status().isOk());

                assertThat(appender.list)
                    .singleElement()
                    .satisfies(
                        event ->
                            assertThat(event.getMDCPropertyMap())
                                .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736"));
              });
    } finally {
      controllerLog.detachAppender(appender);
    }
  }

  /** A controller that logs one line, so the MDC of a request thread can be inspected. */
  @org.springframework.web.bind.annotation.RestController
  static class MdcProbe {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MdcProbe.class);

    @org.springframework.web.bind.annotation.GetMapping("/api/test/mdc")
    String mdc() {
      LOG.info("handling a request, traceId in MDC is {}", MDC.get("traceId"));
      return "ok";
    }

    /** Registers the probe. */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class Config {

      @org.springframework.context.annotation.Bean
      MdcProbe mdcProbe() {
        return new MdcProbe();
      }
    }
  }
}
