package com.openelements.spring.base.services.webhook.data;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Service handling webhook CRUD operations. */
@Service
public class WebhookDataService extends AbstractDbBackedDataService<WebhookEntity, WebhookDto> {

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
}
