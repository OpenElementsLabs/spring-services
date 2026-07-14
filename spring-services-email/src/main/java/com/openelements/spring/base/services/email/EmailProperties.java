package com.openelements.spring.base.services.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the email sending integration.
 *
 * <p>Bound from the {@code open-elements.email} prefix. SMTP connection settings themselves remain
 * under Spring Boot's standard {@code spring.mail.*} prefix.
 */
@ConfigurationProperties(prefix = "open-elements.email")
public class EmailProperties {

  private String from;

  private String fromName;

  /** Creates the properties holder with unset values. */
  public EmailProperties() {}

  /**
   * Returns the configured sender email address.
   *
   * @return the sender email address, or {@code null} if not configured
   */
  public String getFrom() {
    return from;
  }

  /**
   * Sets the sender email address.
   *
   * @param from the sender email address
   */
  public void setFrom(final String from) {
    this.from = from;
  }

  /**
   * Returns the optional display name used for the sender address.
   *
   * @return the sender display name, or {@code null} if not configured
   */
  public String getFromName() {
    return fromName;
  }

  /**
   * Sets the optional display name used for the sender address.
   *
   * @param fromName the sender display name
   */
  public void setFromName(final String fromName) {
    this.fromName = fromName;
  }
}
