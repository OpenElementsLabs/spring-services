package com.openelements.spring.base.services.webhook;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Configuration for webhook-related beans. */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = WebhookConfig.class)
public class WebhookConfig {

  /** Bean name of the executor that delivers webhook calls. */
  public static final String EXECUTOR_BEAN_NAME = "webhookTaskExecutor";

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  /** Creates the configuration; webhook beans are declared via the annotated factory methods. */
  public WebhookConfig() {}

  @Bean
  RestClient webhookRestClient() {
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT);
    factory.setReadTimeout(TIMEOUT);
    return RestClient.builder().requestFactory(factory).build();
  }

  /**
   * Delivers webhook calls, one virtual thread per call. No default candidate, so that it never
   * competes with an {@link java.util.concurrent.Executor} of the application and never makes
   * Spring Boot's {@code applicationTaskExecutor} back off.
   *
   * @return the executor
   */
  @Bean(name = EXECUTOR_BEAN_NAME, defaultCandidate = false)
  SimpleAsyncTaskExecutor webhookTaskExecutor() {
    final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("webhook-");
    executor.setVirtualThreads(true);
    return executor;
  }
}
