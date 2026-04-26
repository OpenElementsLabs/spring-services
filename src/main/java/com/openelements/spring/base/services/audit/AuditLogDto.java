package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.WithId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO view of an {@link AuditLogEntity}.
 *
 * @param id the audit entry id
 * @param entityType the simple class name of the audited DTO type
 * @param entityId the id of the audited entity
 * @param action the kind of lifecycle event that produced this entry
 * @param user the authenticated user, or {@code "System"} for unauthenticated operations
 * @param createdAt the timestamp at which the action was recorded
 */
@Schema(description = "Audit log entry for a data lifecycle event")
public record AuditLogDto(
    @Schema(description = "Audit entry id", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(description = "Audited DTO type (simple class name)") String entityType,
    @Schema(description = "Id of the audited entity") UUID entityId,
    @Schema(description = "Kind of lifecycle event") AuditAction action,
    @Schema(description = "User who performed the action, or \"System\"") String user,
    @Schema(description = "Action timestamp") Instant createdAt)
    implements WithId {

  /**
   * Maps an entity to its DTO representation.
   *
   * @param entity the persisted entity
   * @return a fully populated DTO
   */
  public static AuditLogDto fromEntity(final AuditLogEntity entity) {
    return new AuditLogDto(
        entity.getId(),
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getAction(),
        entity.getUserName(),
        entity.getCreatedAt());
  }
}
