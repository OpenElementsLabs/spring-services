package com.openelements.spring.base.services.webhook;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.services.webhook.payload.WebhookDataEventPayload;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for event instances after transaction commit and delegates HTTP POST calls to {@link
 * WebhookSender} for each active webhook.
 */
@Component
public class WebhookEventListener {

  private final WebhookSender webhookSender;

  public WebhookEventListener(final WebhookSender webhookSender) {
    this.webhookSender = Objects.requireNonNull(webhookSender, "webhookSender must not be null");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public <T extends WithId> void handleOnObjectCreate(final OnObjectCreate<T> event) {
    Objects.requireNonNull(event, "event must not be null");
    handle(WebhookDataEventType.CREATED, event.getType(), event.getData());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public <T extends WithId> void handleOnObjectUpdate(final OnObjectUpdate<T> event) {
    Objects.requireNonNull(event, "event must not be null");
    handle(WebhookDataEventType.UPDATED, event.getType(), event.getData());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public <T extends WithId> void handleOnObjectDelete(final OnObjectDelete<T> event) {
    Objects.requireNonNull(event, "event must not be null");
    handle(WebhookDataEventType.DELETED, event.getType(), event.getData());
  }

  private <T extends WithId> void handle(WebhookDataEventType eventType, Class<T> type, T data) {
    if (supportsWebhook(type, WebhookDataEventType.DELETED)) {
      final WebhookDataEventPayload payload =
          WebhookDataEventPayload.of(WebhookDataEventType.DELETED, type, data);
      webhookSender.sendAndTrack(payload);
    }
  }

  private <T extends WithId> boolean supportsWebhook(
      final Class<T> type, final WebhookDataEventType eventType) {
    return true;
  }
}
