package com.openelements.spring.services.webhook;

import java.util.UUID;

/**
 * Internal Spring event published by services when a domain entity changes.
 * Consumed by {@link WebhookEventListener} to fire webhook HTTP calls.
 *
 * @param eventType the type of domain event
 * @param entityId  the ID of the affected entity
 * @param data      the entity DTO (null for delete events)
 */
public record WebhookEvent(
        WebhookEventType eventType,
        UUID entityId,
        Object data
) {
}
