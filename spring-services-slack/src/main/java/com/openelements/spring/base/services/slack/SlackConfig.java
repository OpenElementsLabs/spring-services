package com.openelements.spring.base.services.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration for Slack messaging beans. */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SlackConfig.class)
@EnableConfigurationProperties(SlackProperties.class)
public class SlackConfig {

  /** Creates the configuration. */
  public SlackConfig() {}

  /**
   * The Slack SDK entry point for every Web API call, with the bot token and with user tokens.
   *
   * <p>The shared SDK instance is not closed with the context ({@code destroyMethod = ""}): it is a
   * JVM-wide singleton that other contexts in the same JVM may still use.
   *
   * @return the Slack SDK
   */
  @Bean(destroyMethod = "")
  @ConditionalOnMissingBean
  Slack slack() {
    return Slack.getInstance();
  }

  @Bean
  @Nullable MethodsClient slackMethodsClient(final SlackProperties properties, final Slack slack) {
    final String token = properties.getToken();
    if (token == null || token.isBlank()) {
      return null;
    }
    return slack.methods(token);
  }
}
