package com.openelements.spring.base.services.scim;

import com.openelements.spring.base.data.DbSchema;
import com.openelements.spring.base.services.user.UserRepository;
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
 * Ensures the reserved {@link ScimServicePrincipal SCIM service principal} row exists in the
 * {@code users} table on startup, so SCIM writes can be attributed to it in the audit log.
 *
 * <p>Mirrors {@code SystemUserInitializer} (spec 008): the row carries a pre-assigned reserved UUID,
 * so it is inserted through {@link JdbcTemplate} (a direct SQL {@code INSERT}) rather than
 * {@code UserRepository#save}, which would treat the pre-assigned id as a detached-entity update.
 * The insert is idempotent — guarded by an {@code existsById} check and a
 * {@link DataIntegrityViolationException} catch for the concurrent-startup race. The principal is
 * created {@code active = false} so it can never authenticate interactively.
 */
@Component
public class ScimServicePrincipalInitializer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ScimServicePrincipalInitializer.class);

  private static final String INSERT_SQL =
      "INSERT INTO "
          + DbSchema.NAME
          + ".users (id, user_name, name, active, deleted, updated_at, created_at) "
          + "VALUES (?, ?, ?, FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

  private final UserRepository userRepository;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Creates a new initializer that ensures the SCIM service principal row exists at startup.
   *
   * @param userRepository the repository used to check for the existing principal row
   * @param jdbcTemplate the template used to insert the principal row
   */
  public ScimServicePrincipalInitializer(
      final UserRepository userRepository, final JdbcTemplate jdbcTemplate) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @Override
  @Transactional
  public void run(final ApplicationArguments args) {
    if (userRepository.existsById(ScimServicePrincipal.ID)) {
      return;
    }
    try {
      jdbcTemplate.update(
          INSERT_SQL, ScimServicePrincipal.ID, ScimServicePrincipal.USER_NAME,
          ScimServicePrincipal.NAME);
      LOG.info("Created SCIM service principal row (id={})", ScimServicePrincipal.ID);
    } catch (final DataIntegrityViolationException e) {
      LOG.debug("SCIM service principal row was created concurrently by another instance.", e);
    }
  }
}
