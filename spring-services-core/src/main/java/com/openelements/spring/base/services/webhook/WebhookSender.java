package com.openelements.spring.base.services.webhook;

import com.openelements.spring.base.services.webhook.data.WebhookDataService;
import com.openelements.spring.base.services.webhook.data.WebhookDto;
import com.openelements.spring.base.services.webhook.payload.WebhookDataEventPayload;
import com.openelements.spring.base.services.webhook.payload.WebhookEventPayload;
import com.openelements.spring.base.services.webhook.payload.WebhookPingEventPayload;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Sends webhook HTTP POST calls and records the response status. Data events are delivered on the
 * {@linkplain WebhookConfig#EXECUTOR_BEAN_NAME webhook executor}, so that a slow or unreachable
 * receiver never blocks the operation that caused the event; a ping is delivered on the calling
 * thread.
 */
@Component
public class WebhookSender {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookSender.class);

  private final RestClient restClient;

  private final WebhookDataService webhookDataService;

  private final Executor executor;

  /**
   * Creates a new webhook sender.
   *
   * @param webhookRestClient the pre-configured {@link RestClient} used to deliver webhook calls
   * @param webhookDataService the service used to look up webhooks and record delivery results
   * @param webhookTaskExecutor the executor that delivers data events
   */
  public WebhookSender(
      final RestClient webhookRestClient,
      final WebhookDataService webhookDataService,
      @Qualifier(WebhookConfig.EXECUTOR_BEAN_NAME) final Executor webhookTaskExecutor) {
    this.restClient =
        Objects.requireNonNull(webhookRestClient, "webhookRestClient must not be null");
    this.webhookDataService =
        Objects.requireNonNull(webhookDataService, "webhookDataService must not be null");
    this.executor =
        Objects.requireNonNull(webhookTaskExecutor, "webhookTaskExecutor must not be null");
  }

  /**
   * Delivers a payload to a single webhook and records the resulting HTTP status.
   *
   * @param webhook the target webhook
   * @param payload the payload to deliver
   */
  private void sendAndTrack(final WebhookDto webhook, final WebhookEventPayload payload) {
    int status;
    try {
      final ResponseEntity<Void> response =
          restClient
              .post()
              .uri(webhook.url())
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .toBodilessEntity();
      status = response.getStatusCode().value();
      LOG.debug("Webhook call for URL {} returned status {}", webhook.url(), status);
    } catch (final HttpClientErrorException e) {
      status = e.getStatusCode().value();
      LOG.warn(
          "Webhook call returned client error for URL {}: {}", webhook.url(), e.getStatusCode());
    } catch (final HttpServerErrorException e) {
      status = e.getStatusCode().value();
      LOG.warn(
          "Webhook call returned server error for URL {}: {}", webhook.url(), e.getStatusCode());
    } catch (final ResourceAccessException e) {
      status = isTimeout(e) ? -1 : 0;
      LOG.warn("Webhook call failed for URL {}: {}", webhook.url(), e.getMessage());
    } catch (final Exception e) {
      status = 0;
      LOG.warn("Webhook call failed for URL {}: {}", webhook.url(), e.getMessage());
    }
    webhookDataService.recordCall(webhook.id(), status, Instant.now());
  }

  private boolean isTimeout(final ResourceAccessException e) {
    return e.getCause() instanceof SocketTimeoutException;
  }

  /**
   * Delivers a data-event payload to every currently active webhook. Returns as soon as the calls
   * are handed to the webhook executor; each call runs on its own.
   *
   * @param payload the payload to deliver
   */
  public void sendAndTrack(final WebhookDataEventPayload payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    webhookDataService
        .findAllActive()
        .forEach(webhook -> executor.execute(() -> sendAndTrackLogged(webhook, payload)));
  }

  private void sendAndTrackLogged(final WebhookDto webhook, final WebhookEventPayload payload) {
    try {
      sendAndTrack(webhook, payload);
    } catch (final RuntimeException e) {
      LOG.error("Failed to record the call of webhook {}", webhook.id(), e);
    }
  }

  /**
   * Sends a ping payload to a single webhook to verify that its endpoint is reachable. Runs on the
   * calling thread, so the recorded status is available as soon as this method returns.
   *
   * @param id the id of the webhook to ping
   * @throws IllegalArgumentException if no webhook with that id exists
   */
  public void ping(final UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    final WebhookDto dto =
        webhookDataService
            .findById(id)
            .orElseThrow(
                () -> new IllegalArgumentException("Webhook with id " + id + " not found"));
    sendAndTrack(dto, WebhookPingEventPayload.create());
  }
}
