package com.openelements.spring.base.services.webhook.data;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a webhook registration in the CRM system.
 */
@Entity
@Table(name = "webhooks")
public class WebhookEntity extends AbstractEntity {

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_status")
    private Integer lastStatus;

    @Column(name = "last_called_at")
    private Instant lastCalledAt;

    /**
     * Default constructor required by JPA.
     */
    public WebhookEntity() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = Objects.requireNonNull(url, "url must not be null");
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public Integer getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(final Integer lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Instant getLastCalledAt() {
        return lastCalledAt;
    }

    public void setLastCalledAt(final Instant lastCalledAt) {
        this.lastCalledAt = lastCalledAt;
    }

    @Override
    public String toString() {
        return "WebhookEntity[id=" + id() + ", url=" + url + ", active=" + active + "]";
    }
}
