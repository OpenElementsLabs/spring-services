package com.openelements.spring.services.webhook;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON payload sent to webhook URLs when a domain event occurs.
 *
 * @param eventId   unique identifier for idempotency
 * @param eventType the type of domain event
 * @param timestamp when the event occurred
 * @param entityId  the ID of the affected entity
 * @param data      the entity DTO (null for delete events)
 */
public record WebhookEventPayload(
        UUID eventId,
        WebhookEventType eventType,
        Instant timestamp,
        UUID entityId,
        Object data
) {
}
