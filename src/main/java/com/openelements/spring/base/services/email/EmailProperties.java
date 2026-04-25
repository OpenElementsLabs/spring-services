package com.openelements.spring.base.services.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the email sending integration.
 *
 * <p>Bound from the {@code open-elements.email} prefix. SMTP connection settings themselves remain
 * under Spring Boot's standard {@code spring.mail.*} prefix.
 */
@Component
@ConfigurationProperties(prefix = "open-elements.email")
public class EmailProperties {

  private String from;

  private String fromName;

  public String getFrom() {
    return from;
  }

  public void setFrom(final String from) {
    this.from = from;
  }

  public String getFromName() {
    return fromName;
  }

  public void setFromName(final String fromName) {
    this.fromName = fromName;
  }
}
