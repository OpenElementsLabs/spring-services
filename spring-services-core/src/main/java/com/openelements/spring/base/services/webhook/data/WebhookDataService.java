package com.openelements.spring.base.services.webhook.data;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Service handling webhook CRUD operations. */
@Service
public class WebhookDataService extends AbstractDbBackedDataService<WebhookEntity, WebhookDto> {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookDataService.class);

  private final WebhookRepository webhookRepository;

  /**
   * Creates a new webhook service.
   *
   * @param webhookRepository the repository used to persist and query webhook registrations
   * @param eventPublisher Spring's event publisher, used to broadcast lifecycle events
   */
  public WebhookDataService(
      final WebhookRepository webhookRepository, final ApplicationEventPublisher eventPublisher) {
    super(eventPublisher);
    this.webhookRepository =
        Objects.requireNonNull(webhookRepository, "webhookRepository must not be null");
  }

  @Override
  protected WebhookEntity createDetachedEntity() {
    return new WebhookEntity();
  }

  @Override
  protected WebhookRepository getRepository() {
    return webhookRepository;
  }

  @Override
  protected void updateEntity(WebhookEntity entity, WebhookDto data) {
    entity.setActive(data.active());
    entity.setUrl(data.url());
    entity.setLastStatus(data.lastStatus());
    entity.setLastCalledAt(data.lastCalledAt());
  }

  @Override
  protected WebhookDto toData(WebhookEntity entity) {
    return new WebhookDto(
        entity.getId(),
        entity.getUrl(),
        entity.isActive(),
        entity.getLastStatus(),
        entity.getLastCalledAt());
  }

  /**
   * Returns all currently active webhook registrations.
   *
   * @return the active webhooks as DTOs
   */
  public List<WebhookDto> findAllActive() {
    return getRepository().findAllByActiveTrue().stream().map(this::toData).toList();
  }

  /**
   * Records the result of a call to a webhook. Only the status and the call time are written, and
   * no lifecycle event is published: the result of a delivery is bookkeeping, not a change of the
   * registration, and an event would trigger a webhook call for every webhook call. A webhook that
   * was deleted in the meantime is ignored.
   *
   * @param id the id of the called webhook
   * @param status the HTTP status of the response, {@code 0} for a failed call or {@code -1} for a
   *     timeout
   * @param calledAt when the webhook was called
   */
  public void recordCall(final UUID id, final int status, final Instant calledAt) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(calledAt, "calledAt must not be null");
    webhookRepository
        .findById(id)
        .ifPresentOrElse(
            entity -> {
              entity.setLastStatus(status);
              entity.setLastCalledAt(calledAt);
            },
            () -> LOG.debug("Webhook {} was deleted before its call could be recorded", id));
  }
}
