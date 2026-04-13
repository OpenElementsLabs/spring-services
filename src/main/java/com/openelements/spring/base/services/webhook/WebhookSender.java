package com.openelements.spring.base.services.webhook;

import com.openelements.spring.base.services.webhook.data.WebhookDataService;
import com.openelements.spring.base.services.webhook.data.WebhookDto;
import com.openelements.spring.base.services.webhook.payload.WebhookDataEventPayload;
import com.openelements.spring.base.services.webhook.payload.WebhookEventPayload;
import com.openelements.spring.base.services.webhook.payload.WebhookPingEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sends webhook HTTP POST calls asynchronously and persists the response status.
 * Each call runs in its own Spring-managed async thread with transactional context.
 */
@Component
public class WebhookSender {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookSender.class);

    private final RestClient restClient;

    private final WebhookDataService webhookDataService;

    public WebhookSender(final RestClient webhookRestClient, final WebhookDataService webhookDataService) {
        this.restClient = Objects.requireNonNull(webhookRestClient, "webhookRestClient must not be null");
        this.webhookDataService = Objects.requireNonNull(webhookDataService, "webhookDataService must not be null");
    }

    @Async
    protected void sendAndTrack(final WebhookDto webhook, final WebhookEventPayload payload) {
        int status;
        try {
            final ResponseEntity<Void> response = restClient.post()
                    .uri(webhook.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            status = response.getStatusCode().value();
            LOG.debug("Webhook call for URL {} returned status {}", webhook.url(), status);
        } catch (final HttpClientErrorException e) {
            status = e.getStatusCode().value();
            LOG.warn("Webhook call returned client error for URL {}: {}", webhook.url(), e.getStatusCode());
        } catch (final HttpServerErrorException e) {
            status = e.getStatusCode().value();
            LOG.warn("Webhook call returned server error for URL {}: {}", webhook.url(), e.getStatusCode());
        } catch (final ResourceAccessException e) {
            status = isTimeout(e) ? -1 : 0;
            LOG.warn("Webhook call failed for URL {}: {}", webhook.url(), e.getMessage());
        } catch (final Exception e) {
            status = 0;
            LOG.warn("Webhook call failed for URL {}: {}", webhook.url(), e.getMessage());
        }
        final WebhookDto updated = new WebhookDto(
                webhook.id(),
                webhook.url(),
                webhook.active(),
                status,
                Instant.now()
        );
        webhookDataService.save(updated);
    }

    private boolean isTimeout(final ResourceAccessException e) {
        return e.getCause() instanceof SocketTimeoutException;
    }

    public void sendAndTrack(WebhookDataEventPayload payload) {
        webhookDataService.findAllActive()
                .forEach(webhook -> sendAndTrack(webhook, payload));
    }

    public void ping(final UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        final WebhookDto dto = webhookDataService.
                findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Webhook with id " + id + " not found"));
        sendAndTrack(dto, WebhookPingEventPayload.create());
    }

}
