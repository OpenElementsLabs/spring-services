package com.openelements.spring.base.services.webhook.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for a webhook ping, used to verify that an endpoint is reachable.
 *
 * @param eventId the unique identifier of this ping event
 * @param timestamp the moment at which the ping was created
 */
public record WebhookPingEventPayload(UUID eventId, Instant timestamp)
    implements WebhookEventPayload {

  /**
   * Creates a new ping payload with a fresh event id and the current timestamp.
   *
   * @return a new ping payload
   */
  public static WebhookEventPayload create() {
    return new WebhookPingEventPayload(UUID.randomUUID(), Instant.now());
  }
}
