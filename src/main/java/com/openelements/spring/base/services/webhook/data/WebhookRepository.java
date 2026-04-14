package com.openelements.spring.base.services.webhook.data;

import com.openelements.spring.base.data.EntityRepository;
import java.util.List;

/** Repository for webhook entities. */
public interface WebhookRepository extends EntityRepository<WebhookEntity> {

  /**
   * Returns all webhooks that are currently active.
   *
   * @return list of active webhooks
   */
  List<WebhookEntity> findAllByActiveTrue();
}
