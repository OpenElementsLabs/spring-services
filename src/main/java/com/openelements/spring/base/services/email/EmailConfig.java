package com.openelements.spring.base.services.email;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration for email sending beans. */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
public class EmailConfig {

  @Bean
  @ConfigurationProperties(prefix = "open-elements.email")
  public EmailProperties emailProperties() {
    return new EmailProperties();
  }
}
