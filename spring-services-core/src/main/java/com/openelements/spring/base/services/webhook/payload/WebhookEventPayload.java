package com.openelements.spring.base.services.webhook.payload;

import java.time.Instant;
import java.util.UUID;

/** Common contract for all payloads delivered to webhook endpoints. */
public interface WebhookEventPayload {

  /**
   * Returns the unique identifier of this event, used by receivers for idempotency.
   *
   * @return the event id
   */
  UUID eventId();

  /**
   * Returns the moment at which the event occurred.
   *
   * @return the event timestamp
   */
  Instant timestamp();
}
