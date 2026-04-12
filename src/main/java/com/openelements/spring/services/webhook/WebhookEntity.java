package com.openelements.spring.services.webhook;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a webhook registration in the CRM system.
 */
@Entity
@Table(name = "webhooks")
public class WebhookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_status")
    private Integer lastStatus;

    @Column(name = "last_called_at")
    private Instant lastCalledAt;

    /**
     * Default constructor required by JPA.
     */
    public WebhookEntity() {
    }

    public UUID getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WebhookEntity that = (WebhookEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WebhookEntity[id=" + id + ", url=" + url + ", active=" + active + "]";
    }
}
