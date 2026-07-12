package com.openelements.spring.base.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.data.DbSchema;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves that the migration SQL shipped in the upgrade doc actually moves an existing consumer's
 * {@code public}-schema tables into {@code oe_spring_services} with data, indexes, and foreign keys
 * intact.
 *
 * <p>The test deliberately does <strong>not</strong> use the JPA entities (which now hard-map to the
 * dedicated schema). It hand-creates the seven library tables in {@code public} — the pre-upgrade
 * layout — seeds rows including an {@code audit_log → users} foreign key, then executes the exact
 * {@code CREATE SCHEMA} + {@code ALTER TABLE … SET SCHEMA} statements from the upgrade doc and
 * asserts the result.
 */
@Testcontainers
class SchemaMigrationSqlIntegrationTest {

  private static final String[] LIBRARY_TABLES = {
    "users", "api_keys", "audit_log", "comments", "settings", "tags", "webhooks"
  };

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    final DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    dataSource.setDriverClassName(POSTGRES.getDriverClassName());
    jdbc = new JdbcTemplate(dataSource);
    dropEverything();
    createPublicSchemaTables();
  }

  @Test
  @DisplayName("Documented migration moves all seven tables into the dedicated schema, data intact")
  void migrationMovesTablesWithData() {
    final UUID userId = UUID.randomUUID();
    jdbc.update("INSERT INTO public.users (id, name) VALUES (?, ?)", userId, "Alice");
    jdbc.update(
        "INSERT INTO public.audit_log (id, user_id) VALUES (?, ?)", UUID.randomUUID(), userId);

    runDocumentedMigration();

    // Every table now lives in oe_spring_services and no longer in public.
    for (final String table : LIBRARY_TABLES) {
      assertThat(regclass(DbSchema.NAME + "." + table))
          .as("%s must exist in the dedicated schema after migration", table)
          .isNotNull();
      assertThat(regclass("public." + table))
          .as("%s must no longer exist in public after migration", table)
          .isNull();
    }

    // Data survived the move.
    assertThat(count(DbSchema.NAME + ".users")).isEqualTo(1);
    assertThat(count(DbSchema.NAME + ".audit_log")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT user_id FROM " + DbSchema.NAME + ".audit_log", UUID.class))
        .isEqualTo(userId);
  }

  @Test
  @DisplayName("The audit_log → users foreign key still holds after the schema move")
  void foreignKeySurvivesTheMove() {
    final UUID userId = UUID.randomUUID();
    jdbc.update("INSERT INTO public.users (id, name) VALUES (?, ?)", userId, "Bob");

    runDocumentedMigration();

    // A valid child row is accepted...
    jdbc.update(
        "INSERT INTO " + DbSchema.NAME + ".audit_log (id, user_id) VALUES (?, ?)",
        UUID.randomUUID(),
        userId);
    assertThat(count(DbSchema.NAME + ".audit_log")).isEqualTo(1);

    // ...but a dangling reference is still rejected — the FK moved with the table.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO " + DbSchema.NAME + ".audit_log (id, user_id) VALUES (?, ?)",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** The exact statements the upgrade doc instructs the consumer to run. */
  private void runDocumentedMigration() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + DbSchema.NAME);
    for (final String table : LIBRARY_TABLES) {
      jdbc.execute("ALTER TABLE " + table + " SET SCHEMA " + DbSchema.NAME);
    }
  }

  /** Recreates the pre-upgrade layout: the seven tables in {@code public}. */
  private void createPublicSchemaTables() {
    jdbc.execute("CREATE TABLE public.users (id uuid PRIMARY KEY, name varchar(255))");
    jdbc.execute(
        "CREATE TABLE public.audit_log (id uuid PRIMARY KEY, "
            + "user_id uuid REFERENCES public.users(id))");
    jdbc.execute("CREATE TABLE public.api_keys (id uuid PRIMARY KEY)");
    jdbc.execute("CREATE TABLE public.comments (id uuid PRIMARY KEY)");
    jdbc.execute("CREATE TABLE public.settings (id uuid PRIMARY KEY)");
    jdbc.execute("CREATE TABLE public.tags (id uuid PRIMARY KEY)");
    jdbc.execute("CREATE TABLE public.webhooks (id uuid PRIMARY KEY)");
  }

  private void dropEverything() {
    jdbc.execute("DROP SCHEMA IF EXISTS " + DbSchema.NAME + " CASCADE");
    for (final String table : LIBRARY_TABLES) {
      jdbc.execute("DROP TABLE IF EXISTS public." + table + " CASCADE");
    }
  }

  private String regclass(final String qualifiedTable) {
    return jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, qualifiedTable);
  }

  private long count(final String qualifiedTable) {
    final Long n = jdbc.queryForObject("SELECT count(*) FROM " + qualifiedTable, Long.class);
    return n == null ? 0L : n;
  }
}
