package com.openelements.spring.base.services.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link SettingsDataService} against a real PostgreSQL container.
 *
 * <h2>What is tested</h2>
 *
 * <p>End-to-end CRUD on the {@code settings} table — specifically the regression that motivated
 * the {@code findByKey} / {@code deleteByKey} routing. {@link SettingsEntity}'s primary key is a
 * UUID (inherited from {@code AbstractEntity}), while the {@code key} column is the business
 * identifier callers use. The pre-fix code called {@code findById(String)} which tripped
 * Hibernate's id-type check before any SQL was issued. This test confirms:
 *
 * <ul>
 *   <li>{@code get / set / delete} all round-trip through the business-key column.
 *   <li>{@code set} is a real upsert: the second write under the same key mutates the existing
 *       row in place rather than inserting a duplicate (verified by {@code count() == 1L}).
 *   <li>Distinct keys produce distinct rows (no accidental cross-pollination via the UUID PK).
 *   <li>{@code delete} on an unknown key is a no-op, not an exception.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. The settings table is truncated in {@code
 * @BeforeEach} so each test runs against an empty row set. The integration footprint is
 * intentional: the id-type mismatch the test guards against only surfaces against a real JPA
 * provider with a real database — no in-memory substitute would catch it.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. {@link SettingsDataService} and {@link SettingsRepository}
 * are real beans wired by Spring; the database is a real Postgres in a container. Mocking would
 * defeat the purpose of the regression guard.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("SettingsDataService Integration Tests")
class SettingsDataServiceIntegrationTest {

  @Autowired private SettingsDataService settingsDataService;

  @Autowired private SettingsRepository settingsRepository;

  @BeforeEach
  void setUp() {
    settingsRepository.deleteAll();
  }

  @Nested
  @DisplayName("get")
  class GetTest {

    @Test
    @DisplayName("get(missing key) against real Postgres returns Optional.empty() — no id-type exception.")
    void shouldReturnEmptyForUnknownKeyWithoutThrowing() {
      final Optional<String> result = settingsDataService.get("missing.key");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("A value written via set(...) is readable via get(...) in a fresh transaction.")
    void shouldReturnValueAfterSet() {
      settingsDataService.set("brevo.api-key", "secret-123");

      final Optional<String> result = settingsDataService.get("brevo.api-key");

      assertThat(result).isPresent().contains("secret-123");
    }
  }

  @Nested
  @DisplayName("set")
  class SetTest {

    @Test
    @DisplayName("First set(key, value) inserts exactly one row into the settings table.")
    void shouldInsertExactlyOneRowOnFirstWrite() {
      settingsDataService.set("app.name", "open-crm");

      assertThat(settingsRepository.count()).isEqualTo(1L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm");
    }

    /**
     * Pins the upsert contract end-to-end against Postgres: the second {@code set(...)} under the
     * same key must mutate the existing row, not create a duplicate. {@code count() == 1L} would
     * fail if the service ever issued a blind {@code INSERT} for an existing key.
     */
    @Test
    @DisplayName("Second set(key, value) for the same key updates in place — row count stays at 1.")
    void shouldUpdateInPlaceOnSecondWrite() {
      settingsDataService.set("app.name", "open-crm");
      settingsDataService.set("app.name", "open-crm-v2");

      assertThat(settingsRepository.count()).isEqualTo(1L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm-v2");
    }

    @Test
    @DisplayName("Two set(...) calls with different keys produce two independent rows.")
    void shouldKeepDistinctKeysIndependent() {
      settingsDataService.set("app.name", "open-crm");
      settingsDataService.set("brevo.api-key", "secret-123");

      assertThat(settingsRepository.count()).isEqualTo(2L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm");
      assertThat(settingsDataService.get("brevo.api-key")).contains("secret-123");
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTest {

    @Test
    @DisplayName("delete(key) for a present key removes the row and a subsequent get returns empty.")
    void shouldRemoveAnExistingSetting() {
      settingsDataService.set("brevo.api-key", "secret-123");

      settingsDataService.delete("brevo.api-key");

      assertThat(settingsDataService.get("brevo.api-key")).isEmpty();
      assertThat(settingsRepository.count()).isZero();
    }

    @Test
    @DisplayName("delete(unknown key) is a no-op — no exception, unrelated rows untouched.")
    void shouldBeNoopForUnknownKey() {
      settingsDataService.set("app.name", "open-crm");

      settingsDataService.delete("missing.key");

      assertThat(settingsRepository.count()).isEqualTo(1L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm");
    }
  }
}
