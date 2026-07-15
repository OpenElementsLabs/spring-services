package com.openelements.spring.base.services.user;

import com.openelements.spring.base.data.DbSchema;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the {@link SystemUser System User} row exists in the {@code users} table on application
 * startup. Runs as an {@link ApplicationRunner} so that schema initialization (Hibernate DDL or any
 * consumer-side migration tool) has completed before the row is inserted.
 *
 * <p>Idempotent: on every startup after the first the {@code existsById} check short-circuits the
 * insert. The {@link DataIntegrityViolationException} catch guards the race where two instances
 * pass the check simultaneously.
 *
 * <p>Inserts the row through {@link JdbcTemplate} rather than {@link UserRepository#save} because
 * the row carries a pre-assigned id (the reserved nil UUID). Both Spring Data's {@code save} and
 * {@code EntityManager.persist} reject the pre-assigned id: {@code save} classifies the entity as
 * detached and issues an UPDATE that hits no row, and {@code persist} rejects "detached entity"
 * outright. A direct SQL INSERT bypasses Hibernate's new-vs-detached heuristic and is sufficient
 * for the single bootstrap row.
 */
@Component
public class SystemUserInitializer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SystemUserInitializer.class);

  private static final String INSERT_SQL =
      "INSERT INTO "
          + DbSchema.NAME
          + ".users (id, sub, user_name, name, active, deleted, updated_at, created_at) "
          + "VALUES (?, ?, ?, ?, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

  private final UserRepository userRepository;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Creates a new initializer that ensures the System User row exists at startup.
   *
   * @param userRepository the repository used to check for the existing System User
   * @param jdbcTemplate the template used to insert the System User row
   */
  public SystemUserInitializer(
      final UserRepository userRepository, final JdbcTemplate jdbcTemplate) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @Override
  @Transactional
  public void run(final ApplicationArguments args) {
    if (userRepository.existsById(SystemUser.ID)) {
      return;
    }
    try {
      jdbcTemplate.update(
          INSERT_SQL, SystemUser.ID, SystemUser.SUB, SystemUser.SUB, SystemUser.NAME);
      LOG.info("Created System User row (id={}, sub={})", SystemUser.ID, SystemUser.SUB);
    } catch (final DataIntegrityViolationException e) {
      LOG.debug("System User row was created concurrently by another instance — ignoring.", e);
    }
  }
}
