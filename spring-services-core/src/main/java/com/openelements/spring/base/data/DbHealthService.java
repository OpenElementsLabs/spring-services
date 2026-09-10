package com.openelements.spring.base.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers the single question "is the database reachable right now?" for the {@link DataSource}
 * the application is configured with.
 *
 * <p>The check is a real round-trip: it borrows a connection from the pool and executes a
 * statement on it. That distinguishes it from merely inspecting the {@code DataSource} bean —
 * an application that booted successfully can still have a dead database (network partition,
 * exhausted pool, database restarted, credentials rotated), and a bean-level check would report
 * healthy in all of those cases.
 *
 * <p>Typical use is an application-owned health or readiness endpoint:
 *
 * <pre>{@code
 * @RestController
 * class HealthController {
 *
 *   private final DbHealthService dbHealthService;
 *
 *   HealthController(DbHealthService dbHealthService) {
 *     this.dbHealthService = dbHealthService;
 *   }
 *
 *   @GetMapping("/health")
 *   HealthDTO health() {
 *     return new HealthDTO(
 *         HealthStatus.UP,
 *         dbHealthService.isDatabaseReachable() ? HealthStatus.UP : HealthStatus.DOWN);
 *   }
 * }
 * }</pre>
 *
 * <p>The library deliberately ships no endpoint and no status enum of its own: HTTP path,
 * authorization, response shape and the vocabulary used for "up" and "down" are the
 * application's decisions. This service only supplies the fact.
 *
 * <p>The returned value is a point-in-time observation and is never cached — every call issues a
 * new round-trip. Callers that poll frequently (Kubernetes probes, uptime monitors) should
 * therefore be aware that each call occupies a pooled connection for the duration of the query,
 * and that a saturated pool makes the call block for up to the pool's connection timeout.
 */
public class DbHealthService {

  private static final Logger LOG = LoggerFactory.getLogger(DbHealthService.class);

  /**
   * The cheapest round-trip that proves a borrowed connection can actually execute a statement:
   * it touches no table, takes no lock, and is accepted by every database this library is used
   * with (PostgreSQL in production, H2 in tests).
   */
  private static final String VALIDATION_QUERY = "SELECT 1";

  private final DataSource dataSource;

  /**
   * Creates the service for the given data source.
   *
   * @param dataSource the data source to probe, must not be {@code null}
   * @throws NullPointerException if {@code dataSource} is {@code null}
   */
  public DbHealthService(final DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
  }

  /**
   * Executes a validation query against the configured data source and reports whether it
   * succeeded.
   *
   * <p>Never throws: any {@link SQLException} — and any {@link RuntimeException} raised by the
   * connection pool, for example a connection-acquisition timeout — is caught, logged at
   * {@code WARN} (with the stack trace at {@code DEBUG}) and reported as {@code false}. A health
   * check that fails with an exception would force every caller into a {@code try}/{@code catch}
   * to arrive at the same answer.
   *
   * @return {@code true} if a connection could be borrowed and the validation query executed,
   *     {@code false} otherwise
   */
  public boolean isDatabaseReachable() {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(VALIDATION_QUERY);
      return true;
    } catch (final SQLException | RuntimeException e) {
      LOG.warn("Database is not reachable: {}", e.getMessage());
      LOG.debug("Database health check failed", e);
      return false;
    }
  }
}
