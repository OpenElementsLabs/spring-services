package com.openelements.spring.base.services.webhook.payload;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.services.webhook.WebhookDataEventType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JSON payload sent to webhook URLs when a domain event occurs.
 *
 * @param <T> the type of the entity DTO carried by this payload
 * @param eventId unique identifier for idempotency
 * @param eventType the type of domain event
 * @param dataClass the entity class
 * @param timestamp when the event occurred
 * @param data the entity DTO (null for delete events)
 */
public record WebhookDataEventPayload<T extends WithId>(
    UUID eventId, WebhookDataEventType eventType, Class<T> dataClass, T data, Instant timestamp)
    implements WebhookEventPayload {

  /** Validates the components, rejecting {@code null} values and a {@code null} data id. */
  public WebhookDataEventPayload {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(data, "data must not be null");
    Objects.requireNonNull(data.id(), "data.id must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  /**
   * Creates a payload with a fresh event id and the current timestamp for the given entity.
   *
   * @param <T> the type of the entity DTO
   * @param eventType the type of domain event
   * @param dataclass the entity class
   * @param data the entity DTO
   * @return a new payload describing the event
   */
  public static <T extends WithId> WebhookDataEventPayload<T> of(
      WebhookDataEventType eventType, Class<T> dataclass, T data) {
    Objects.requireNonNull(data, "data must not be null");
    return new WebhookDataEventPayload<>(
        UUID.randomUUID(), eventType, dataclass, data, Instant.now());
  }

  /**
   * Creates a payload for the given entity, inferring the entity class from its runtime type.
   *
   * @param <T> the type of the entity DTO
   * @param eventType the type of domain event
   * @param data the entity DTO
   * @return a new payload describing the event
   */
  public static <T extends WithId> WebhookDataEventPayload<T> of(
      WebhookDataEventType eventType, T data) {
    Objects.requireNonNull(data, "data must not be null");
    // getClass() erases to Class<? extends WithId>; the runtime class of a T value is a Class<T>
    // (or a subtype), so this cast is safe — it only ever narrows to the actual DTO type.
    @SuppressWarnings("unchecked")
    final Class<T> dataClass = (Class<T>) data.getClass();
    return of(eventType, dataClass, data);
  }
}
