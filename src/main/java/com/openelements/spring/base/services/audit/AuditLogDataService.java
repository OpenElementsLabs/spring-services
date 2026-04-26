package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that persists {@link AuditLogEntity} rows.
 *
 * <p>Constructed with {@code publishEvents = false} so that writing an audit row never produces a
 * recursive {@code OnObjectCreate} event.
 */
@Service
public class AuditLogDataService extends AbstractDbBackedDataService<AuditLogEntity, AuditLogDto> {

  private final AuditLogRepository auditLogRepository;

  public AuditLogDataService(
      @NonNull final AuditLogRepository auditLogRepository,
      @NonNull final ApplicationEventPublisher eventPublisher) {
    super(eventPublisher, false);
    this.auditLogRepository =
        Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
  }

  @Override
  protected AuditLogEntity createDetachedEntity() {
    return new AuditLogEntity();
  }

  @Override
  protected void updateEntity(final AuditLogEntity entity, final AuditLogDto data) {
    entity.setEntityType(data.entityType());
    entity.setEntityId(data.entityId());
    entity.setAction(data.action());
    entity.setUserName(data.user());
  }

  @Override
  protected AuditLogDto toData(final AuditLogEntity entity) {
    return AuditLogDto.fromEntity(entity);
  }

  @Override
  protected EntityRepository<AuditLogEntity> getRepository() {
    return auditLogRepository;
  }

  /**
   * Returns audit entries for the given simple class name, ordered by creation time (newest first).
   *
   * @param entityType the simple class name to filter on
   * @param pageable pagination information
   * @return matching entries as a page of DTOs
   */
  public Page<AuditLogDto> findByEntityType(
      @NonNull final String entityType, @NonNull final Pageable pageable) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    Objects.requireNonNull(pageable, "pageable must not be null");
    return auditLogRepository
        .findByEntityTypeOrderByCreatedAtDesc(entityType, pageable)
        .map(AuditLogDto::fromEntity);
  }

  /**
   * Returns audit entries written for the given user, ordered by creation time (newest first).
   *
   * @param user the user name (or {@code "System"})
   * @param pageable pagination information
   * @return matching entries as a page of DTOs
   */
  public Page<AuditLogDto> findByUser(
      @NonNull final String user, @NonNull final Pageable pageable) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(pageable, "pageable must not be null");
    return auditLogRepository
        .findByUserNameOrderByCreatedAtDesc(user, pageable)
        .map(AuditLogDto::fromEntity);
  }

  /**
   * Returns audit entries matching both type and user, ordered by creation time (newest first).
   *
   * @param entityType the simple class name to filter on
   * @param user the user name to filter on
   * @param pageable pagination information
   * @return matching entries as a page of DTOs
   */
  public Page<AuditLogDto> findByEntityTypeAndUser(
      @NonNull final String entityType,
      @NonNull final String user,
      @NonNull final Pageable pageable) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(pageable, "pageable must not be null");
    return auditLogRepository
        .findByEntityTypeAndUserNameOrderByCreatedAtDesc(entityType, user, pageable)
        .map(AuditLogDto::fromEntity);
  }

  /**
   * Returns all distinct entity types that have at least one audit entry.
   *
   * @return distinct entity type names, possibly empty
   */
  public List<String> findAllEntityTypes() {
    return auditLogRepository.findDistinctEntityTypes();
  }

  /**
   * Persists a single audit log entry in its own transaction.
   *
   * <p>The {@link Propagation#REQUIRES_NEW} setting guarantees that a rollback of the surrounding
   * business transaction does not undo the audit row, and conversely that an audit-side failure
   * (e.g. constraint violation) cannot roll back the surrounding transaction.
   *
   * @param entityType the simple class name of the audited DTO
   * @param entityId the id of the audited entity
   * @param action the kind of lifecycle event
   * @param user the user name to record
   * @return the persisted DTO
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AuditLogDto createEntry(
      @NonNull final String entityType,
      @NonNull final UUID entityId,
      @NonNull final AuditAction action,
      @NonNull final String user) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(user, "user must not be null");
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType(entityType);
    entity.setEntityId(entityId);
    entity.setAction(action);
    entity.setUserName(user);
    return AuditLogDto.fromEntity(auditLogRepository.save(entity));
  }
}
