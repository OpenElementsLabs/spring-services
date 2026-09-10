package com.openelements.spring.base.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link DbHealthService} against a real PostgreSQL container.
 *
 * <h2>What is tested</h2>
 *
 * <p>Three things the unit test cannot prove:
 *
 * <ul>
 *   <li>The validation query is accepted by a real PostgreSQL and the service reports
 *       {@code true} — a mocked {@link java.sql.Statement} would happily "execute" invalid SQL.
 *   <li>The service is injectable as a bean the library registers itself (via {@link DataConfig}),
 *       without the application declaring it.
 *   <li>Repeated calls stay {@code true}, which would fail if the check leaked pooled
 *       connections instead of closing them.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * starts a real Postgres via Testcontainers and provides the {@link DataSource} the injected
 * {@link DbHealthService} probes. The unreachable case is deliberately not reproduced here —
 * stopping the container would make the test slow and order-dependent for the other tests
 * sharing it; those branches are covered by {@link DbHealthServiceTest}.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. Both the service and the data source are real beans, and the
 * database is a real Postgres in a container — the whole point of the test is that the round-trip
 * actually happens.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("DbHealthService Integration Tests")
class DbHealthServiceIntegrationTest {

  @Autowired private DbHealthService dbHealthService;

  @Test
  @DisplayName("reports a running PostgreSQL as reachable")
  void shouldReportRunningDatabaseAsReachable() {
    // GIVEN a running Postgres container wired as the application's DataSource

    // WHEN
    final boolean reachable = dbHealthService.isDatabaseReachable();

    // THEN
    assertThat(reachable).as("the container database is up").isTrue();
  }

  @Test
  @DisplayName("stays reachable across repeated checks, so no pooled connection is leaked")
  void shouldStayReachableAcrossRepeatedChecks() {
    // GIVEN the default Hikari pool size, which is far below the iteration count

    // WHEN / THEN
    for (int i = 0; i < 25; i++) {
      assertThat(dbHealthService.isDatabaseReachable())
          .as("check #%d must still find the database reachable", i + 1)
          .isTrue();
    }
  }
}
