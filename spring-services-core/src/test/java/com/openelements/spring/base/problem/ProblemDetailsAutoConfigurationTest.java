package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.trace.TraceAutoConfiguration;
import com.openelements.spring.base.trace.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Context tests for {@link ProblemDetailsAutoConfiguration}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Wiring that cannot be proven by a unit test, and that silently degrades when it breaks:
 *
 * <ul>
 *   <li><b>Spring Boot's own beans back off.</b> {@code BasicErrorController} and Boot's {@code
 *       ProblemDetailsExceptionHandler} are both {@code @ConditionalOnMissingBean}, so the library
 *       wins only because its auto-configuration is declared {@code before} Boot's. If that ordering
 *       were ever dropped the application would still start — it would just answer some errors in
 *       Boot's format and some in ours, which is the kind of split nobody notices until a client
 *       does.
 *   <li><b>The catch-all resolver sits directly after {@code ExceptionHandlerExceptionResolver}.</b>
 *       One position too early and it shadows every application {@code @ExceptionHandler}; one
 *       position too late and {@code @ResponseStatus} exceptions never reach it. The test asserts
 *       the index in the real resolver chain.
 *   <li><b>{@code enabled=false} registers nothing at all</b> and gives Boot's controller back.
 *   <li><b>An application bean replaces the library's</b>, for the factory, the MVC advice and the
 *       trace resolver.
 *   <li><b>An invalid {@code type-base} fails the context</b> rather than producing broken type URIs.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link WebApplicationContextRunner} with the real auto-configurations, including Boot's, so the
 * conditions are evaluated exactly as they are at runtime. No mocks.
 */
@DisplayName("ProblemDetailsAutoConfiguration")
class ProblemDetailsAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ProblemDetailsAutoConfiguration.class,
                  TraceAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  ErrorMvcAutoConfiguration.class))
          .withUserConfiguration(ServletPath.class);

  @Test
  @DisplayName("By default every bean of the feature is registered.")
  void everyBeanIsRegisteredByDefault() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ProblemFactory.class);
          assertThat(context).hasSingleBean(ProblemResponseWriter.class);
          assertThat(context).hasSingleBean(ProblemDetailsExceptionHandler.class);
          assertThat(context).hasSingleBean(ErrorController.class);
          assertThat(context).hasSingleBean(ProblemDetailsProperties.class);
        });
  }

  @Test
  @DisplayName("Spring Boot's BasicErrorController and problem advice back off.")
  void springBootsOwnBeansBackOff() {
    runner.run(
        context -> {
          assertThat(context.getBean(ErrorController.class))
              .isInstanceOf(ProblemErrorController.class);
          assertThat(context.getBeansOfType(ResponseEntityExceptionHandler.class).values())
              .singleElement()
              .isInstanceOf(ProblemDetailsExceptionHandler.class);
        });
  }

  /**
   * Spring MVC decides whether a bean is a request handler from the type the bean factory reports,
   * which for a {@code @Bean} method is its declared return type. Declaring {@link ErrorController}
   * there leaves {@code /error} unmapped while every other signal looks healthy — the context
   * starts, the bean exists, Boot's controller backs off — and every container error answers
   * {@code 404} instead of its real status. This pins the declared type.
   */
  @Test
  @DisplayName("The error controller is registered under its concrete type, so /error is mapped.")
  void errorControllerIsRegisteredUnderItsConcreteType() {
    runner.run(
        context ->
            assertThat(context.getType("problemErrorController"))
                .isEqualTo(ProblemErrorController.class));
  }

  @Test
  @DisplayName("The catch-all resolver is inserted directly after ExceptionHandlerExceptionResolver.")
  void catchAllResolverSitsAfterTheAdviceResolver() {
    runner.run(
        context -> {
          final List<HandlerExceptionResolver> chain = resolverChain(context);
          final int adviceResolver =
              indexOf(chain, ExceptionHandlerExceptionResolver.class);
          final int catchAll = indexOf(chain, ProblemHandlerExceptionResolver.class);

          assertThat(adviceResolver).as("advice resolver must be present").isNotNegative();
          assertThat(catchAll).isEqualTo(adviceResolver + 1);
        });
  }

  @Test
  @DisplayName("enabled=false registers nothing and gives Spring Boot's controller back.")
  void disabledRegistersNothing() {
    runner
        .withPropertyValues("openelements.problem-details.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ProblemFactory.class);
              assertThat(context).doesNotHaveBean(ProblemResponseWriter.class);
              assertThat(context).doesNotHaveBean(ProblemDetailsExceptionHandler.class);
              assertThat(context).doesNotHaveBean(ProblemErrorController.class);
              assertThat(context.getBean(ErrorController.class).getClass().getSimpleName())
                  .isEqualTo("BasicErrorController");
              assertThat(resolverChain(context))
                  .noneMatch(ProblemHandlerExceptionResolver.class::isInstance);
            });
  }

  @Test
  @DisplayName("An application ProblemFactory replaces the library's.")
  void applicationFactoryWins() {
    runner
        .withUserConfiguration(ApplicationFactory.class)
        .run(
            context -> {
              assertThat(context.getBean(ProblemFactory.class))
                  .isInstanceOf(ApplicationFactory.FixedProblemFactory.class);
            });
  }

  @Test
  @DisplayName("An application ResponseEntityExceptionHandler suppresses the library's advice.")
  void applicationAdviceSuppressesTheLibrarysAdvice() {
    runner
        .withUserConfiguration(ApplicationOwnAdvice.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ProblemDetailsExceptionHandler.class);
              assertThat(context).hasSingleBean(ApplicationOwnAdvice.class);
              // The catch-all still runs, so an unexpected exception is still a problem.
              assertThat(resolverChain(context))
                  .anyMatch(ProblemHandlerExceptionResolver.class::isInstance);
            });
  }

  @Test
  @DisplayName("An application TraceIdResolver replaces the default.")
  void applicationTraceIdResolverWins() {
    runner
        .withUserConfiguration(ApplicationTraceIdResolver.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(TraceIdResolver.class);
              assertThat(context.getBean(TraceIdResolver.class).resolve(null))
                  .isEqualTo(ApplicationTraceIdResolver.FIXED_ID);
            });
  }

  @Test
  @DisplayName("A relative type-base fails the context.")
  void relativeTypeBaseFailsStartUp() {
    runner
        .withPropertyValues("openelements.problem-details.type-base=/problems/")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("openelements.problem-details.type-base"));
  }

  @Test
  @DisplayName("A type-base without a trailing slash fails the context.")
  void typeBaseWithoutTrailingSlashFailsStartUp() {
    runner
        .withPropertyValues("openelements.problem-details.type-base=https://example.org/problems")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("openelements.problem-details.type-base"));
  }

  /**
   * Reads the resolver chain Spring MVC assembled, flattening the composite the {@code
   * DispatcherServlet} is actually given.
   */
  private static List<HandlerExceptionResolver> resolverChain(
      final org.springframework.context.ApplicationContext context) {
    final HandlerExceptionResolver bean =
        context.getBean("handlerExceptionResolver", HandlerExceptionResolver.class);
    return bean instanceof HandlerExceptionResolverComposite composite
        ? composite.getExceptionResolvers()
        : List.of(bean);
  }

  private static int indexOf(
      final List<HandlerExceptionResolver> chain, final Class<?> resolverType) {
    for (int i = 0; i < chain.size(); i++) {
      if (resolverType.isInstance(chain.get(i))) {
        return i;
      }
    }
    return -1;
  }

  // --- application-supplied beans -----------------------------------------------------------------

  /**
   * Supplies the {@code DispatcherServletPath} that {@code ErrorMvcAutoConfiguration} needs to map
   * the container's error page. In a real application {@code DispatcherServletAutoConfiguration}
   * contributes it; a {@link WebApplicationContextRunner} has no servlet registration to derive it
   * from, so the root path is declared here.
   */
  @Configuration(proxyBeanMethods = false)
  static class ServletPath {

    @Bean
    org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath dispatcherServletPath() {
      return () -> "";
    }
  }

  /** Stands in for an application that builds its own problem bodies. */
  @Configuration(proxyBeanMethods = false)
  static class ApplicationFactory {

    @Bean
    ProblemFactory applicationProblemFactory() {
      return new FixedProblemFactory();
    }

    /** Returns the same empty problem for everything; only its type matters to the test. */
    static final class FixedProblemFactory implements ProblemFactory {
      @Override
      public ProblemDetail create(
          final ProblemType type, final @Nullable String detail, final HttpServletRequest request) {
        return ProblemDetail.forStatus(type.status());
      }

      @Override
      public ProblemDetail create(
          final ProblemException exception, final HttpServletRequest request) {
        return ProblemDetail.forStatus(exception.type().status());
      }
    }
  }

  /**
   * Stands in for an application that extends {@link ResponseEntityExceptionHandler} itself. An
   * empty subclass is enough — its mere existence is what makes the library's advice back off.
   */
  @org.springframework.web.bind.annotation.RestControllerAdvice
  static class ApplicationOwnAdvice extends ResponseEntityExceptionHandler {}

  /** Stands in for an application that takes its trace ID from somewhere else. */
  @Configuration(proxyBeanMethods = false)
  static class ApplicationTraceIdResolver {

    static final String FIXED_ID = "aaaaaaaabbbbbbbbccccccccdddddddd";

    @Bean
    TraceIdResolver applicationTraceIdResolver() {
      return request -> FIXED_ID;
    }
  }
}
