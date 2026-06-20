package com.openelements.spring.base.services.webhook;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Configuration for webhook-related beans. */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = WebhookConfig.class)
public class WebhookConfig {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @Bean
  RestClient webhookRestClient() {
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT);
    factory.setReadTimeout(TIMEOUT);
    return RestClient.builder().requestFactory(factory).build();
  }
}
