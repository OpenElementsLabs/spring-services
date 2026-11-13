package com.example.problemapp;

import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application side of the problem-details web tests: the controllers, the {@code @Validated}
 * service they call, and the servlet path {@code ErrorMvcAutoConfiguration} needs.
 *
 * <p>It declares no error handling at all. Every problem the tests assert has to be produced by the
 * library.
 */
@Configuration(proxyBeanMethods = false)
public class ProblemTestApp {

  /** Creates the configuration. */
  public ProblemTestApp() {}

  /**
   * Contributes the controller carrying one endpoint per error under test.
   *
   * @param service the {@code @Validated} service one endpoint calls
   * @return the controller
   */
  @Bean
  ProblemTestController problemTestController(final ProblemTestService service) {
    return new ProblemTestController(service);
  }

  /**
   * Contributes the {@code @Validated} controller, whose parameter failures arrive as a {@code
   * ConstraintViolationException} rather than Spring MVC's own exception.
   *
   * @return the controller
   */
  @Bean
  ValidatedProblemTestController validatedProblemTestController() {
    return new ValidatedProblemTestController();
  }

  /**
   * Contributes the {@code @Validated} service that stands in for a bean deeper down the stack.
   *
   * @return the service
   */
  @Bean
  ProblemTestService problemTestService() {
    return new ProblemTestService();
  }

  /**
   * Supplies the servlet path {@code ErrorMvcAutoConfiguration} maps the container's error page
   * against. A {@code WebApplicationContextRunner} has no servlet registration to derive it from.
   *
   * @return the root path
   */
  @Bean
  DispatcherServletPath dispatcherServletPath() {
    return () -> "";
  }
}
