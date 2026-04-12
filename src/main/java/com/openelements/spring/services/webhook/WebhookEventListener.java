package com.openelements.spring.services.webhook;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Listens for {@link WebhookEvent} instances after transaction commit and delegates
 * HTTP POST calls to {@link WebhookSender} for each active webhook.
 */
@Component
public class WebhookEventListener {

    private final WebhookRepository webhookRepository;
    private final WebhookSender webhookSender;

    public WebhookEventListener(final WebhookRepository webhookRepository, final WebhookSender webhookSender) {
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository must not be null");
        this.webhookSender = Objects.requireNonNull(webhookSender, "webhookSender must not be null");
    }

    /**
     * Handles a webhook event by delegating to {@link WebhookSender} for each active webhook.
     * Each call runs asynchronously via Spring's {@code @Async} on the sender.
     *
     * @param event the domain event to broadcast
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEvent(final WebhookEvent event) {
        final List<WebhookEntity> activeWebhooks = webhookRepository.findAllByActiveTrue();
        if (activeWebhooks.isEmpty()) {
            return;
        }

        final WebhookEventPayload payload = new WebhookEventPayload(
                UUID.randomUUID(),
                event.eventType(),
                Instant.now(),
                event.entityId(),
                event.data()
        );

        for (final WebhookEntity webhook : activeWebhooks) {
            webhookSender.sendAndTrack(webhook, payload);
        }
    }
}
