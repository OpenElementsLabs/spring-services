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
 * <p>Locks in the fix for the id-type mismatch: {@link SettingsEntity}'s primary key is a UUID
 * (inherited from {@code AbstractEntity}), so {@link SettingsRepository} declares the JPA generic
 * as {@code UUID} and the service looks settings up via {@code findByKey} / {@code deleteByKey}
 * against the unique business-key column. The pre-fix code called {@code findById(String)} which
 * tripped Hibernate's id-type check before any database access.
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
    void shouldReturnEmptyForUnknownKeyWithoutThrowing() {
      final Optional<String> result = settingsDataService.get("missing.key");

      assertThat(result).isEmpty();
    }

    @Test
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
    void shouldInsertExactlyOneRowOnFirstWrite() {
      settingsDataService.set("app.name", "open-crm");

      assertThat(settingsRepository.count()).isEqualTo(1L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm");
    }

    @Test
    void shouldUpdateInPlaceOnSecondWrite() {
      settingsDataService.set("app.name", "open-crm");
      settingsDataService.set("app.name", "open-crm-v2");

      assertThat(settingsRepository.count()).isEqualTo(1L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm-v2");
    }

    @Test
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
    void shouldRemoveAnExistingSetting() {
      settingsDataService.set("brevo.api-key", "secret-123");

      settingsDataService.delete("brevo.api-key");

      assertThat(settingsDataService.get("brevo.api-key")).isEmpty();
      assertThat(settingsRepository.count()).isZero();
    }

    @Test
    void shouldBeNoopForUnknownKey() {
      settingsDataService.set("app.name", "open-crm");

      settingsDataService.delete("missing.key");

      assertThat(settingsRepository.count()).isEqualTo(1L);
      assertThat(settingsDataService.get("app.name")).contains("open-crm");
    }
  }
}
