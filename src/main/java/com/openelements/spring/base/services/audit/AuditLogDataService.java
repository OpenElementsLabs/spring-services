package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service that persists {@link AuditLogEntity} rows.
 *
 * <p>Constructed with {@code publishEvents = false} so that writing an audit row never produces a
 * recursive {@code OnObjectCreate} event.
 */
@Service
public class AuditLogDataService extends AbstractDbBackedDataService<AuditLogEntity, AuditLogDto> {

    private final AuditLogRepository auditLogRepository;

    private final UserRepository userRepository;

    public AuditLogDataService(
            @NonNull final AuditLogRepository auditLogRepository,
            @NonNull final UserRepository userRepository,
            @NonNull final ApplicationEventPublisher eventPublisher) {
        super(eventPublisher, false);
        this.auditLogRepository =
                Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
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
        entity.setUser(userRepository.getReferenceById(data.user().id()));
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
     * @param pageable   pagination information
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
     * Returns audit entries written by the user with the given id, ordered by creation time (newest
     * first).
     *
     * @param userId   the id of the user (use {@link
     *                 SystemUser#ID} for unauthenticated actors)
     * @param pageable pagination information
     * @return matching entries as a page of DTOs
     */
    public Page<AuditLogDto> findByUser(
            @NonNull final UUID userId, @NonNull final Pageable pageable) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        return auditLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(AuditLogDto::fromEntity);
    }

    /**
     * Returns audit entries matching both entity type and user id, ordered by creation time (newest
     * first).
     *
     * @param entityType the simple class name to filter on
     * @param userId     the id of the user to filter on
     * @param pageable   pagination information
     * @return matching entries as a page of DTOs
     */
    public Page<AuditLogDto> findByEntityTypeAndUser(
            @NonNull final String entityType,
            @NonNull final UUID userId,
            @NonNull final Pageable pageable) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        return auditLogRepository
                .findByEntityTypeAndUserIdOrderByCreatedAtDesc(entityType, userId, pageable)
                .map(AuditLogDto::fromEntity);
    }

    /**
     * Returns all audit entries created on the given calendar day, ordered by creation time
     * (oldest first).
     *
     * <p>The day boundaries are computed in the system default time zone.
     *
     * @param day the calendar day to query
     * @return matching entries as DTOs, possibly empty
     */
    public List<AuditLogDto> findByDay(@NonNull final LocalDate day) {
        Objects.requireNonNull(day, "day must not be null");
        final ZoneId zone = ZoneId.systemDefault();
        return auditLogRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        day.atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant())
                .stream()
                .map(AuditLogDto::fromEntity)
                .toList();
    }

    /**
     * Returns the {@code limit} most recent audit entries, ordered by creation time (newest first).
     *
     * <p>If fewer than {@code limit} entries exist, the returned list contains all of them.
     *
     * @param limit the maximum number of entries to return, must be greater than zero
     * @return matching entries as DTOs, at most {@code limit} elements, possibly empty
     * @throws IllegalArgumentException if {@code limit} is not positive
     */
    public List<AuditLogDto> findLatest(final int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc(Limit.of(limit)).stream()
                .map(AuditLogDto::fromEntity)
                .toList();
    }

    /**
     * Returns the {@code limit} most recent audit entries for the given entity type, ordered by
     * creation time (newest first).
     *
     * <p>If fewer than {@code limit} entries exist for the type, the returned list contains all of
     * them.
     *
     * @param entityType the simple class name to filter on
     * @param limit      the maximum number of entries to return, must be greater than zero
     * @return matching entries as DTOs, at most {@code limit} elements, possibly empty
     * @throws IllegalArgumentException if {@code limit} is not positive
     */
    public List<AuditLogDto> findLatestByEntityType(
            @NonNull final String entityType, final int limit) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        return auditLogRepository
                .findByEntityTypeOrderByCreatedAtDesc(entityType, Limit.of(limit))
                .stream()
                .map(AuditLogDto::fromEntity)
                .toList();
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
     * @param entityId   the id of the audited entity
     * @param action     the kind of lifecycle event
     * @param user       the user that performed the action
     * @return the persisted DTO
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogDto createEntry(
            @NonNull final String entityType,
            @NonNull final UUID entityId,
            @NonNull final AuditAction action,
            @NonNull final UserEntity user) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(user, "user must not be null");
        final AuditLogEntity entity = new AuditLogEntity();
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setAction(action);
        entity.setUser(user);
        return AuditLogDto.fromEntity(auditLogRepository.save(entity));
    }
}
