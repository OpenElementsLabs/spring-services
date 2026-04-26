package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20)
  private AuditAction action;

  /**
   * The authenticated user who performed the action, or {@code "System"} when the action was
   * triggered without a security context. Stored under the column name {@code user_name} because
   * {@code user} is a reserved word in several SQL dialects.
   */
  @Column(name = "user_name", nullable = false, length = 255)
  private String userName;

  /** Default constructor required by JPA. */
  public AuditLogEntity() {}

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

  public String getUserName() {
    return userName;
  }

  public void setUserName(final String userName) {
    this.userName = Objects.requireNonNull(userName, "userName must not be null");
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
        + ", userName="
        + userName
        + "]";
  }
}
