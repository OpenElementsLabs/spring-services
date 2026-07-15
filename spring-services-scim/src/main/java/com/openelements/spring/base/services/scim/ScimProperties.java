package com.openelements.spring.base.services.scim;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SCIM 2.0 service provider, bound from the
 * {@code openelements.scim} prefix.
 *
 * <p>The whole SCIM feature is gated on {@link #getToken()} being present: without a configured
 * token the module's auto-configuration does not activate, so {@code /scim/v2/**} is not served.
 */
@ConfigurationProperties(prefix = "openelements.scim")
public class ScimProperties {

  /**
   * The static shared-secret bearer token the identity provider (e.g. Authentik) sends verbatim on
   * every SCIM request. It is compared in constant time and never logged.
   */
  private String token;

  /** Creates an empty properties holder populated by Spring from {@code openelements.scim.*}. */
  public ScimProperties() {}

  /**
   * Returns the configured static SCIM bearer token.
   *
   * @return the SCIM bearer token, or {@code null} if SCIM is not configured
   */
  public String getToken() {
    return token;
  }

  /**
   * Sets the static SCIM bearer token.
   *
   * @param token the SCIM bearer token
   */
  public void setToken(final String token) {
    this.token = token;
  }
}
