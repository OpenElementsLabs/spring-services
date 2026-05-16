package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.EntityRepository;
import com.openelements.spring.base.services.user.SystemUser;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link AuditLogEntity} instances.
 */
public interface AuditLogRepository extends EntityRepository<AuditLogEntity> {

    /**
     * Returns audit entries whose entity type matches {@code entityType}, ordered by creation time
     * (newest first).
     *
     * @param entityType the simple class name to filter on
     * @param pageable   pagination information
     * @return matching entries as a page
     */
    Page<AuditLogEntity> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    /**
     * Returns audit entries written by the user with the given id, ordered by creation time (newest
     * first).
     *
     * @param userId   the id of the user (use {@link
     *                 SystemUser#ID} for unauthenticated actors)
     * @param pageable pagination information
     * @return matching entries as a page
     */
    Page<AuditLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Returns audit entries that match both type and user id, ordered by creation time (newest
     * first).
     *
     * @param entityType the simple class name to filter on
     * @param userId     the id of the user to filter on
     * @param pageable   pagination information
     * @return matching entries as a page
     */
    Page<AuditLogEntity> findByEntityTypeAndUserIdOrderByCreatedAtDesc(
            String entityType, UUID userId, Pageable pageable);

    /**
     * Returns all audit entries whose {@code createdAt} timestamp lies in the half-open interval
     * {@code [from, to)}, ordered by creation time (oldest first).
     *
     * @param from inclusive lower bound
     * @param to   exclusive upper bound
     * @return matching entries, possibly empty
     */
    List<AuditLogEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Instant from, Instant to);

    /**
     * Returns the most recent audit entries ordered by creation time (newest first), capped by the
     * given {@link Limit}.
     *
     * @param limit the maximum number of rows to return
     * @return matching entries, possibly empty, at most {@code limit.max()} elements
     */
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc(Limit limit);

    /**
     * Returns all distinct entity types that have at least one audit entry.
     *
     * @return distinct entity type names, possibly empty
     */
    @Query("select distinct a.entityType from AuditLogEntity a")
    List<String> findDistinctEntityTypes();

    /**
     * Bulk-removes every audit entry whose {@code createdAt} timestamp is strictly older than the
     * supplied cutoff. Issued as a single SQL {@code DELETE} so the cleanup is efficient even when
     * the table has accumulated millions of rows.
     *
     * @param cutoff the retention boundary
     * @return the number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("delete from AuditLogEntity a where a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
