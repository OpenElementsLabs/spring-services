package com.openelements.spring.base.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the request trace ID.
 *
 * <p>Registered in core's {@code META-INF/spring/
 * org.springframework.boot.autoconfigure.AutoConfiguration.imports} and active in every servlet web
 * application that has {@code spring-services-core} on the classpath.
 *
 * <p>It is deliberately <strong>not</strong> gated on {@code openelements.problem-details.enabled}:
 * the trace ID is request infrastructure, not part of the error format. An application that turns
 * problem details off keeps {@code traceId} in its log MDC.
 *
 * <p>The filter is registered with an order of {@link SecurityProperties#DEFAULT_FILTER_ORDER}
 * {@code - 1} so it runs before every Spring Security filter chain. Without that, a {@code 401}
 * written by an authentication entry point would be the one response with no trace ID.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceAutoConfiguration {

  /** Creates the auto-configuration; the beans are contributed by the methods below. */
  public TraceAutoConfiguration() {}

  /**
   * Contributes the default resolver, unless the application declares its own.
   *
   * @return a resolver that honours a valid incoming {@code traceparent} and generates an ID
   *     otherwise
   */
  @Bean
  @ConditionalOnMissingBean(TraceIdResolver.class)
  public TraceIdResolver traceParentTraceIdResolver() {
    return new TraceParentTraceIdResolver();
  }

  /**
   * Registers the filter that publishes the trace ID for the duration of a request.
   *
   * @param resolver the resolver to use — the application's bean when it declared one
   * @return the filter registration, ordered ahead of Spring Security
   */
  @Bean
  @ConditionalOnMissingBean(name = "traceContextFilterRegistration")
  public FilterRegistrationBean<?> traceContextFilterRegistration(final TraceIdResolver resolver) {
    final FilterRegistrationBean<TraceContextFilter> registration =
        new FilterRegistrationBean<>(new TraceContextFilter(resolver));
    registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
    return registration;
  }
}
