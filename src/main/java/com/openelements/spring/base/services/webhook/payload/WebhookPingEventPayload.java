package com.openelements.spring.base.services.webhook.payload;

import java.time.Instant;
import java.util.UUID;

public record WebhookPingEventPayload(UUID eventId, Instant timestamp) implements WebhookEventPayload {

    public static WebhookEventPayload create() {
        return new WebhookPingEventPayload(UUID.randomUUID(), Instant.now());
    }
}
