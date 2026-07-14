package com.openelements.spring.base.services.webhook.data;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.DbSchema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** JPA entity representing a webhook registration in the CRM system. */
@Entity
@Table(name = "webhooks", schema = DbSchema.NAME)
public class WebhookEntity extends AbstractEntity {

  /** The target URL that receives the webhook callbacks. */
  @Column(name = "url", nullable = false, length = 2048)
  private String url;

  /** Whether the webhook is currently enabled. */
  @Column(name = "active", nullable = false)
  private boolean active = true;

  /** The HTTP status of the last delivery attempt; {@code null} if never called. */
  @Column(name = "last_status")
  private Integer lastStatus;

  /** The timestamp of the most recent delivery attempt; {@code null} if never called. */
  @Column(name = "last_called_at")
  private Instant lastCalledAt;

  /** Default constructor required by JPA. */
  public WebhookEntity() {}

  /**
   * Returns the target URL that receives the webhook callbacks.
   *
   * @return the target URL
   */
  public String getUrl() {
    return url;
  }

  /**
   * Sets the target URL that receives the webhook callbacks.
   *
   * @param url the target URL
   */
  public void setUrl(final String url) {
    this.url = Objects.requireNonNull(url, "url must not be null");
  }

  /**
   * Returns whether this webhook is currently active.
   *
   * @return {@code true} if the webhook is active, {@code false} otherwise
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Sets whether this webhook is currently active.
   *
   * @param active {@code true} to enable the webhook, {@code false} to disable it
   */
  public void setActive(final boolean active) {
    this.active = active;
  }

  /**
   * Returns the HTTP status of the last delivery attempt.
   *
   * @return the last status ({@code null} if never called, {@code 0} for a connection error,
   *     {@code -1} for a timeout)
   */
  public Integer getLastStatus() {
    return lastStatus;
  }

  /**
   * Sets the HTTP status of the last delivery attempt.
   *
   * @param lastStatus the last status ({@code null} if never called, {@code 0} for a connection
   *     error, {@code -1} for a timeout)
   */
  public void setLastStatus(final Integer lastStatus) {
    this.lastStatus = lastStatus;
  }

  /**
   * Returns the timestamp of the most recent delivery attempt.
   *
   * @return the last call timestamp, or {@code null} if never called
   */
  public Instant getLastCalledAt() {
    return lastCalledAt;
  }

  /**
   * Sets the timestamp of the most recent delivery attempt.
   *
   * @param lastCalledAt the last call timestamp, or {@code null} if never called
   */
  public void setLastCalledAt(final Instant lastCalledAt) {
    this.lastCalledAt = lastCalledAt;
  }

  @Override
  public String toString() {
    return "WebhookEntity[id=" + id() + ", url=" + url + ", active=" + active + "]";
  }
}
