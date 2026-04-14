package com.openelements.spring.base.services.webhook.payload;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.services.webhook.WebhookDataEventType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JSON payload sent to webhook URLs when a domain event occurs.
 *
 * @param eventId unique identifier for idempotency
 * @param eventType the type of domain event
 * @param dataClass the entity class
 * @param timestamp when the event occurred
 * @param data the entity DTO (null for delete events)
 */
public record WebhookDataEventPayload<T extends WithId>(
    UUID eventId, WebhookDataEventType eventType, Class<T> dataClass, T data, Instant timestamp)
    implements WebhookEventPayload {

  public WebhookDataEventPayload {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(data, "data must not be null");
    Objects.requireNonNull(data.id(), "data.id must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  public static <T extends WithId> WebhookDataEventPayload<T> of(
      WebhookDataEventType eventType, Class<T> dataclass, T data) {
    Objects.requireNonNull(data, "data must not be null");
    return new WebhookDataEventPayload<>(
        UUID.randomUUID(), eventType, dataclass, data, Instant.now());
  }

  public static <T extends WithId> WebhookDataEventPayload<T> of(
      WebhookDataEventType eventType, T data) {
    Objects.requireNonNull(data, "data must not be null");
    return of(eventType, (Class<T>) data.getClass(), data);
  }
}
