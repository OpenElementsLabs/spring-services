package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.EntityRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link AuditLogEntity} instances. */
public interface AuditLogRepository extends EntityRepository<AuditLogEntity> {

  /**
   * Returns every audit entry whose entity type matches {@code entityType}.
   *
   * @param entityType the simple class name to filter on
   * @return matching entries, possibly empty
   */
  List<AuditLogEntity> findByEntityType(String entityType);

  /**
   * Returns every audit entry written for the given user.
   *
   * @param userName the user name (or {@code "System"})
   * @return matching entries, possibly empty
   */
  List<AuditLogEntity> findByUserName(String userName);

  /**
   * Returns every audit entry that matches both type and user.
   *
   * @param entityType the simple class name to filter on
   * @param userName the user name to filter on
   * @return matching entries, possibly empty
   */
  List<AuditLogEntity> findByEntityTypeAndUserName(String entityType, String userName);

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
