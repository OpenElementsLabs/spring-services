package com.openelements.spring.base.services.slack;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Slack messaging integration.
 *
 * <p>Bound from the {@code open-elements.slack} prefix.
 */
@ConfigurationProperties(prefix = "open-elements.slack")
public class SlackProperties {

  private String token;

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }
}
