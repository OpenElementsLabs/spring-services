package com.openelements.spring.services.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Objects;

/**
 * Sends webhook HTTP POST calls asynchronously and persists the response status.
 * Each call runs in its own Spring-managed async thread with transactional context.
 */
@Component
public class WebhookSender {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookSender.class);

    private final RestClient restClient;
    private final WebhookRepository webhookRepository;

    public WebhookSender(final RestClient webhookRestClient, final WebhookRepository webhookRepository) {
        this.restClient = Objects.requireNonNull(webhookRestClient, "webhookRestClient must not be null");
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "webhookRepository must not be null");
    }

    /**
     * Sends a webhook payload to the given webhook's URL and persists the response status.
     *
     * @param webhook the webhook entity to call
     * @param payload the payload to send
     */
    @Async
    @Transactional
    public void sendAndTrack(final WebhookEntity webhook, final WebhookEventPayload payload) {
        int status;
        try {
            final ResponseEntity<Void> response = restClient.post()
                    .uri(webhook.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            status = response.getStatusCode().value();
        } catch (final HttpClientErrorException e) {
            status = e.getStatusCode().value();
            LOG.warn("Webhook call returned client error for URL {}: {}", webhook.getUrl(), e.getStatusCode());
        } catch (final HttpServerErrorException e) {
            status = e.getStatusCode().value();
            LOG.warn("Webhook call returned server error for URL {}: {}", webhook.getUrl(), e.getStatusCode());
        } catch (final ResourceAccessException e) {
            status = isTimeout(e) ? -1 : 0;
            LOG.warn("Webhook call failed for URL {}: {}", webhook.getUrl(), e.getMessage());
        } catch (final Exception e) {
            status = 0;
            LOG.warn("Webhook call failed for URL {}: {}", webhook.getUrl(), e.getMessage());
        }
        webhook.setLastStatus(status);
        webhook.setLastCalledAt(Instant.now());
        webhookRepository.save(webhook);
    }

    private boolean isTimeout(final ResourceAccessException e) {
        return e.getCause() instanceof SocketTimeoutException;
    }
}
