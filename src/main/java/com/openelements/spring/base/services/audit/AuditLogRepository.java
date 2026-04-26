package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.EntityRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link AuditLogEntity} instances. */
public interface AuditLogRepository extends EntityRepository<AuditLogEntity> {

  /**
   * Returns audit entries whose entity type matches {@code entityType}, ordered by creation time
   * (newest first).
   *
   * @param entityType the simple class name to filter on
   * @param pageable pagination information
   * @return matching entries as a page
   */
  Page<AuditLogEntity> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

  /**
   * Returns audit entries written for the given user, ordered by creation time (newest first).
   *
   * @param userName the user name (or {@code "System"})
   * @param pageable pagination information
   * @return matching entries as a page
   */
  Page<AuditLogEntity> findByUserNameOrderByCreatedAtDesc(String userName, Pageable pageable);

  /**
   * Returns audit entries that match both type and user, ordered by creation time (newest first).
   *
   * @param entityType the simple class name to filter on
   * @param userName the user name to filter on
   * @param pageable pagination information
   * @return matching entries as a page
   */
  Page<AuditLogEntity> findByEntityTypeAndUserNameOrderByCreatedAtDesc(
      String entityType, String userName, Pageable pageable);

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
