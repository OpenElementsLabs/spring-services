package com.openelements.spring.base.services.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration for Slack messaging beans. */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SlackConfig.class)
@EnableConfigurationProperties(SlackProperties.class)
public class SlackConfig {

  @Bean
  @Nullable MethodsClient slackMethodsClient(final SlackProperties properties) {
    final String token = properties.getToken();
    if (token == null || token.isBlank()) {
      return null;
    }
    return Slack.getInstance().methods(token);
  }
}
