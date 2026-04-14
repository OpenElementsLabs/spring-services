package com.openelements.spring.base.services.webhook.payload;

import java.time.Instant;
import java.util.UUID;

public interface WebhookEventPayload {

  UUID eventId();

  Instant timestamp();
}
