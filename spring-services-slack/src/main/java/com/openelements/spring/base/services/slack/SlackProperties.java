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

  /** Creates the properties with no token configured. */
  public SlackProperties() {}

  /**
   * Returns the configured Slack bot token.
   *
   * @return the Slack bot token, or {@code null} if none is configured
   */
  public String getToken() {
    return token;
  }

  /**
   * Sets the Slack bot token used to authenticate against the Slack API.
   *
   * @param token the Slack bot token
   */
  public void setToken(final String token) {
    this.token = token;
  }
}
