package com.openelements.spring.base.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DbHealthService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That the service reports reachability from the outcome of a real JDBC round-trip and never
 * propagates a failure to its caller:
 *
 * <ul>
 *   <li>A successfully executed validation query yields {@code true}, and both the borrowed
 *       connection and the statement are closed again (they are opened in a
 *       try-with-resources, so a leak here would exhaust the pool under probe traffic).
 *   <li>The statement executed is exactly {@code SELECT 1} — the query is part of the contract
 *       with the database, not an implementation detail, because it must stay table-free and
 *       lock-free.
 *   <li>Every failure mode maps to {@code false} instead of an exception: connection acquisition
 *       failing, statement creation failing, query execution failing, and a
 *       {@link RuntimeException} from the pool (a Hikari connection-acquisition timeout surfaces
 *       as one, so catching only {@link SQLException} would let it escape).
 *   <li>The constructor rejects a {@code null} data source.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Against a mocked {@link DataSource} / {@link Connection} / {@link Statement} chain. No
 * database is involved: the point of these tests is the branch behaviour around the JDBC call —
 * especially the failure branches, which cannot be provoked reliably against a running database.
 * {@link DbHealthServiceIntegrationTest} covers the happy path against a real PostgreSQL.
 *
 * <p><b>Mock-Audit.</b> Three mocks, all unavoidable: {@link Connection} and {@link Statement}
 * are JDBC SPI interfaces with dozens of methods each, so hand-written stubs would be far more
 * code than the tests themselves, and the failure cases require making those methods throw on
 * demand.
 */
@DisplayName("DbHealthService Tests")
class DbHealthServiceTest {

  @Nested
  @DisplayName("when the database answers")
  class Reachable {

    @Test
    @DisplayName("reports the database as reachable")
    void shouldReportReachableWhenValidationQuerySucceeds() throws SQLException {
      // GIVEN
      final Statement statement = mock(Statement.class);
      final Connection connection = mock(Connection.class);
      when(connection.createStatement()).thenReturn(statement);
      final DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenReturn(connection);
      final DbHealthService service = new DbHealthService(dataSource);

      // WHEN
      final boolean reachable = service.isDatabaseReachable();

      // THEN
      assertThat(reachable).as("a successful validation query means reachable").isTrue();
    }

    @Test
    @DisplayName("executes SELECT 1 and releases connection and statement")
    void shouldExecuteValidationQueryAndCloseResources() throws SQLException {
      // GIVEN
      final Statement statement = mock(Statement.class);
      final Connection connection = mock(Connection.class);
      when(connection.createStatement()).thenReturn(statement);
      final DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenReturn(connection);
      final DbHealthService service = new DbHealthService(dataSource);

      // WHEN
      service.isDatabaseReachable();

      // THEN
      verify(statement).execute("SELECT 1");
      verify(statement).close();
      verify(connection).close();
    }
  }

  @Nested
  @DisplayName("when the database does not answer")
  class Unreachable {

    @Test
    @DisplayName("reports the database as unreachable if no connection can be borrowed")
    void shouldReportUnreachableWhenConnectionCannotBeAcquired() throws SQLException {
      // GIVEN
      final DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
      final DbHealthService service = new DbHealthService(dataSource);

      // WHEN
      final boolean reachable = service.isDatabaseReachable();

      // THEN
      assertThat(reachable).as("no connection means unreachable").isFalse();
    }

    @Test
    @DisplayName("reports the database as unreachable if the statement cannot be created")
    void shouldReportUnreachableWhenStatementCannotBeCreated() throws SQLException {
      // GIVEN
      final Connection connection = mock(Connection.class);
      when(connection.createStatement()).thenThrow(new SQLException("connection is closed"));
      final DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenReturn(connection);
      final DbHealthService service = new DbHealthService(dataSource);

      // WHEN
      final boolean reachable = service.isDatabaseReachable();

      // THEN
      assertThat(reachable).as("a dead connection means unreachable").isFalse();
      verify(connection).close();
    }

    @Test
    @DisplayName("reports the database as unreachable if the validation query fails")
    void shouldReportUnreachableWhenValidationQueryFails() throws SQLException {
      // GIVEN
      final Statement statement = mock(Statement.class);
      when(statement.execute(anyString())).thenThrow(new SQLException("terminating connection"));
      final Connection connection = mock(Connection.class);
      when(connection.createStatement()).thenReturn(statement);
      final DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection()).thenReturn(connection);
      final DbHealthService service = new DbHealthService(dataSource);

      // WHEN
      final boolean reachable = service.isDatabaseReachable();

      // THEN
      assertThat(reachable).as("a failing validation query means unreachable").isFalse();
    }

    @Test
    @DisplayName("swallows a RuntimeException from the connection pool")
    void shouldReportUnreachableWhenPoolThrowsRuntimeException() throws SQLException {
      // GIVEN
      final DataSource dataSource = mock(DataSource.class);
      when(dataSource.getConnection())
          .thenThrow(new IllegalStateException("pool has been shut down"));
      final DbHealthService service = new DbHealthService(dataSource);

      // WHEN / THEN
      assertThatNoException().isThrownBy(service::isDatabaseReachable);
      assertThat(service.isDatabaseReachable())
          .as("an unchecked pool failure means unreachable, not a propagated exception")
          .isFalse();
    }
  }

  @Nested
  @DisplayName("construction")
  class Construction {

    @Test
    @DisplayName("rejects a null data source")
    void shouldRejectNullDataSource() {
      // GIVEN / WHEN / THEN
      assertThatNullPointerException()
          .isThrownBy(() -> new DbHealthService(null))
          .withMessage("dataSource must not be null");
    }
  }
}
