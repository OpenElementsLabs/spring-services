package com.openelements.spring.services.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for webhook entities.
 */
public interface WebhookRepository extends JpaRepository<WebhookEntity, UUID> {

    /**
     * Returns all webhooks that are currently active.
     *
     * @return list of active webhooks
     */
    List<WebhookEntity> findAllByActiveTrue();
}
