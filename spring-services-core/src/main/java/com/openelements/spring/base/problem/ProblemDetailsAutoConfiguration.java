package com.openelements.spring.base.problem;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configuration for RFC 9457 error responses.
 *
 * <p>Registered in core's {@code META-INF/spring/
 * org.springframework.boot.autoconfigure.AutoConfiguration.imports} and active in every servlet web
 * application that has {@code spring-services-core} on the classpath, unless {@code
 * openelements.problem-details.enabled=false}.
 *
 * <h2>Why it runs before Spring Boot's web auto-configurations</h2>
 *
 * <p>Boot contributes two beans this feature replaces, and both back off only for a bean that
 * already exists: {@code BasicErrorController} ({@code @ConditionalOnMissingBean(ErrorController)})
 * and its own {@code ProblemDetailsExceptionHandler} ({@code
 * @ConditionalOnMissingBean(ResponseEntityExceptionHandler)}). Declaring {@code before} both
 * auto-configurations is what makes the library's beans win; without it the outcome would depend on
 * auto-configuration ordering, which is unspecified.
 *
 * <h2>What an application can replace</h2>
 *
 * <p>Every bean here is guarded by {@link ConditionalOnMissingBean}:
 *
 * <ul>
 *   <li>a {@link ProblemFactory} bean replaces the problem shape everywhere, including for the
 *       {@code 401} and {@code 403} written by the security filter chain;
 *   <li>a {@link ResponseEntityExceptionHandler} subclass takes over the standard MVC mappings
 *       entirely — the library's advice is then not registered at all, while the catch-all resolver
 *       keeps turning unexpected exceptions into a {@code 500} problem;
 *   <li>an {@link ErrorController} bean takes over the error dispatch.
 * </ul>
 */
@AutoConfiguration(before = {WebMvcAutoConfiguration.class, ErrorMvcAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "openelements.problem-details",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ProblemDetailsProperties.class)
public class ProblemDetailsAutoConfiguration {

  /** Creates the auto-configuration; the beans are contributed by the methods below. */
  public ProblemDetailsAutoConfiguration() {}

  /**
   * Contributes the factory that builds every problem, unless the application declares its own.
   *
   * @param properties the configured type base and {@code instance} switch
   * @return the problem factory
   */
  @Bean
  @ConditionalOnMissingBean(ProblemFactory.class)
  public ProblemFactory problemFactory(final ProblemDetailsProperties properties) {
    return new DefaultProblemFactory(properties);
  }

  /**
   * Contributes the writer the security handlers use to render a problem outside Spring MVC.
   *
   * @param objectMapper the application's Jackson mapper
   * @return the response writer
   */
  @Bean
  @ConditionalOnMissingBean(ProblemResponseWriter.class)
  public ProblemResponseWriter problemResponseWriter(final ObjectMapper objectMapper) {
    return new ProblemResponseWriter(objectMapper);
  }

  /**
   * Contributes the advice that maps Spring MVC's own exceptions.
   *
   * <p>Backs off when the application declares a {@link ResponseEntityExceptionHandler} of its own,
   * which is also what makes Spring Boot's equivalent advice back off.
   *
   * @param problemFactory builds the problems
   * @param objectMapper resolves the JSON names a validation pointer is built from
   * @return the exception handler advice
   */
  @Bean
  @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
  public ProblemDetailsExceptionHandler problemDetailsExceptionHandler(
      final ProblemFactory problemFactory, final ObjectMapper objectMapper) {
    return new ProblemDetailsExceptionHandler(problemFactory, objectMapper);
  }

  /**
   * Contributes the error controller that renders the container's {@code ERROR} dispatch.
   *
   * <p>The declared return type is the concrete class, not {@link ErrorController}. Spring MVC
   * decides whether a bean is a request handler by looking for {@code @Controller} on the type the
   * bean factory reports, which for a {@code @Bean} method is its <em>declared</em> return type.
   * Declaring the interface would leave {@code /error} unmapped: the context would start, the bean
   * would exist, {@code BasicErrorController} would still back off — and every container error would
   * answer {@code 404} instead of its real status.
   *
   * <p>The method is package-private because its return type is: the class is an implementation
   * detail, and nothing outside this package has reason to inject it.
   *
   * @param problemFactory builds the problem
   * @param objectMapper serialises it
   * @return the error controller
   */
  @Bean
  @ConditionalOnMissingBean(ErrorController.class)
  ProblemErrorController problemErrorController(
      final ProblemFactory problemFactory, final ObjectMapper objectMapper) {
    return new ProblemErrorController(problemFactory, objectMapper);
  }

  /**
   * Inserts the catch-all resolver directly after Spring's {@link ExceptionHandlerExceptionResolver}.
   *
   * <p>The position is the contract: after the advice resolver, so no application {@code
   * @ExceptionHandler} can be shadowed, and before {@code ResponseStatusExceptionResolver} and
   * {@code DefaultHandlerExceptionResolver}, so {@code @ResponseStatus} and {@code
   * ResponseStatusException} are rendered as problems rather than as Spring's own error page.
   *
   * @param problemFactory builds the problems
   * @param objectMapper serialises them
   * @return the configurer that registers the resolver
   */
  @Bean
  @ConditionalOnMissingBean(name = "problemHandlerExceptionResolverConfigurer")
  public WebMvcConfigurer problemHandlerExceptionResolverConfigurer(
      final ProblemFactory problemFactory, final ObjectMapper objectMapper) {
    return new WebMvcConfigurer() {
      @Override
      public void extendHandlerExceptionResolvers(final List<HandlerExceptionResolver> resolvers) {
        resolvers.add(
            afterExceptionHandlerResolver(resolvers),
            new ProblemHandlerExceptionResolver(problemFactory, objectMapper));
      }
    };
  }

  /**
   * Finds the position directly after Spring's {@link ExceptionHandlerExceptionResolver}.
   *
   * @param resolvers the resolver chain Spring MVC has assembled
   * @return the index to insert at; the end of the chain if the advice resolver is absent, which
   *     only happens when an application has replaced the whole chain
   */
  private static int afterExceptionHandlerResolver(
      final List<HandlerExceptionResolver> resolvers) {
    for (int i = 0; i < resolvers.size(); i++) {
      if (resolvers.get(i) instanceof ExceptionHandlerExceptionResolver) {
        return i + 1;
      }
    }
    return resolvers.size();
  }
}
