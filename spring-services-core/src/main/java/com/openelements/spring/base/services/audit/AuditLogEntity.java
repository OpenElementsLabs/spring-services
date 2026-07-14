package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.DbSchema;
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
@Table(name = "audit_log", schema = DbSchema.NAME)
public class AuditLogEntity extends AbstractEntity {

  /** The simple class name of the audited DTO type. */
  @Column(name = "entity_type", nullable = false, length = 255)
  private String entityType;

  /** The id of the audited entity. */
  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  /** The human readable name of the audited entity at the time of the action. */
  @Column(name = "entity_name", nullable = false)
  private String name;

  /** The kind of lifecycle event this entry records. */
  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20)
  private AuditAction action;

  /**
   * The user that performed the action. References the dedicated {@link SystemUser System User} row
   * for unauthenticated operations (scheduled tasks, startup data loads, API-key requests).
   */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  /** Default constructor required by JPA. */
  public AuditLogEntity() {}

  /**
   * Returns the simple class name of the audited DTO type.
   *
   * @return the audited entity type
   */
  public String getEntityType() {
    return entityType;
  }

  /**
   * Sets the simple class name of the audited DTO type.
   *
   * @param entityType the audited entity type
   */
  public void setEntityType(final String entityType) {
    this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
  }

  /**
   * Returns the id of the audited entity.
   *
   * @return the id of the audited entity
   */
  public UUID getEntityId() {
    return entityId;
  }

  /**
   * Sets the id of the audited entity.
   *
   * @param entityId the id of the audited entity
   */
  public void setEntityId(final UUID entityId) {
    this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
  }

  /**
   * Returns the kind of lifecycle event this entry records.
   *
   * @return the audited action
   */
  public AuditAction getAction() {
    return action;
  }

  /**
   * Sets the kind of lifecycle event this entry records.
   *
   * @param action the audited action
   */
  public void setAction(final AuditAction action) {
    this.action = Objects.requireNonNull(action, "action must not be null");
  }

  /**
   * Returns the user that performed the audited action.
   *
   * @return the acting user, the System User for unauthenticated operations
   */
  public UserEntity getUser() {
    return user;
  }

  /**
   * Sets the user that performed the audited action.
   *
   * @param user the acting user, the System User for unauthenticated operations
   */
  public void setUser(final UserEntity user) {
    this.user = Objects.requireNonNull(user, "user must not be null");
  }

  /**
   * Returns the human readable name of the audited entity at the time of the action.
   *
   * @return the human readable name of the audited entity
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the human readable name of the audited entity.
   *
   * @param name the human readable name of the audited entity
   */
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
