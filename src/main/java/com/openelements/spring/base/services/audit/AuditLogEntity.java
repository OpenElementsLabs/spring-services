package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity persisting a single data-lifecycle event.
 *
 * <p>One row is written for every create, update or delete performed through an {@link
 * com.openelements.spring.base.data.AbstractDbBackedDataService} (other than the audit service
 * itself). The {@code createdAt} timestamp inherited from {@link AbstractEntity} doubles as the
 * action timestamp — there is no separate {@code performedAt} column.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity extends AbstractEntity {

    @Column(name = "entity_type", nullable = false, length = 255)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "entity_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    /**
     * The user that performed the action. References the dedicated {@link
     * SystemUser System User} row for unauthenticated
     * operations (scheduled tasks, startup data loads, API-key requests).
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Default constructor required by JPA.
     */
    public AuditLogEntity() {
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(final String entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(final UUID entityId) {
        this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(final AuditAction action) {
        this.action = Objects.requireNonNull(action, "action must not be null");
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(final UserEntity user) {
        this.user = Objects.requireNonNull(user, "user must not be null");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "AuditLogEntity[id="
                + id()
                + ", entityType="
                + entityType
                + ", entityId="
                + entityId
                + ", action="
                + action
                + ", userId="
                + (user == null ? "null" : user.id())
                + "]";
    }
}
