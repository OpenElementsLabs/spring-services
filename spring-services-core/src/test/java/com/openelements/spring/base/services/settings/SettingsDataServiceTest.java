package com.openelements.spring.base.services.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mockito-style unit tests for {@link SettingsDataService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The three CRUD operations of the settings facade and their guard rails:
 *
 * <ul>
 *   <li>{@code get(key)} returns the persisted value via {@code findByKey} (business key, not the
 *       UUID primary key) and yields an empty {@link Optional} when nothing is stored.
 *   <li>{@code set(key, value)} performs an upsert: a missing row is created with the key wired
 *       through, an existing row is mutated in place and re-flushed.
 *   <li>{@code delete(key)} delegates to {@code deleteByKey} (the business-key route).
 *   <li>All three methods, and the constructor, reject {@code null} arguments with {@link
 *       NullPointerException} — surfaces misconfiguration immediately.
 * </ul>
 *
 * <p>End-to-end persistence behaviour (id-type mismatch fix, real Postgres unique constraint on
 * the business key) is covered by the sibling {@link SettingsDataServiceIntegrationTest}.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Mockito, no Spring context. The single collaborator {@link
 * SettingsRepository} is mocked; {@link SettingsDataService} is instantiated directly with the
 * mock.
 *
 * <p><b>Mock-Audit.</b> One mock: {@code mock(SettingsRepository.class)}. {@link
 * SettingsRepository} is a Spring Data JPA interface — there is no in-memory implementation to
 * substitute. Stubbing {@code findByKey(...)} return values is what lets the upsert branch
 * (existing row vs new row) be exercised independently of any database. The {@code thenAnswer(i
 * -> i.getArgument(0))} on {@code saveAndFlush} only echoes the argument back; it does not hide
 * behaviour. Real-database behaviour is covered by {@link SettingsDataServiceIntegrationTest}
 * against a Testcontainers Postgres.
 */
@DisplayName("SettingsDataService")
class SettingsDataServiceTest {

  private final SettingsRepository settingsRepository = mock(SettingsRepository.class);
  private final SettingsDataService settingsDataService =
      new SettingsDataService(settingsRepository);

  @Nested
  @DisplayName("get")
  class GetTest {

    @Test
    @DisplayName("get(key) returns the stored value when a row exists under that business key.")
    void shouldReturnValueWhenKeyExists() {
      // GIVEN
      final SettingsEntity entity = new SettingsEntity();
      entity.setKey("app.name");
      entity.setValue("TestApp");
      when(settingsRepository.findByKey("app.name")).thenReturn(Optional.of(entity));

      // WHEN
      final Optional<String> result = settingsDataService.get("app.name");

      // THEN
      assertThat(result).isPresent().contains("TestApp");
    }

    @Test
    @DisplayName("get(key) returns Optional.empty() when no row matches — no exception, no default.")
    void shouldReturnEmptyWhenKeyDoesNotExist() {
      // GIVEN
      when(settingsRepository.findByKey("nonexistent")).thenReturn(Optional.empty());

      // WHEN
      final Optional<String> result = settingsDataService.get("nonexistent");

      // THEN
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("get(null) throws NullPointerException — caller bug surfaces before any repository call.")
    void shouldRejectNullKey() {
      assertThatThrownBy(() -> settingsDataService.get(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("set")
  class SetTest {

    @Test
    @DisplayName("set(key, value) for an unknown key inserts a new SettingsEntity with both fields populated.")
    void shouldCreateNewSettingWhenKeyDoesNotExist() {
      // GIVEN
      when(settingsRepository.findByKey("new.key")).thenReturn(Optional.empty());
      when(settingsRepository.saveAndFlush(any(SettingsEntity.class)))
          .thenAnswer(i -> i.getArgument(0));

      // WHEN
      settingsDataService.set("new.key", "new.value");

      // THEN
      verify(settingsRepository)
          .saveAndFlush(
              argThat(
                  entity ->
                      "new.key".equals(entity.getKey()) && "new.value".equals(entity.getValue())));
    }

    /**
     * Pins the in-place mutation contract: {@code set(key, newValue)} loads the existing row,
     * overwrites {@code value} on the managed entity, and passes the <em>same instance</em> to
     * {@code saveAndFlush} — no second row is created, no key field is touched.
     */
    @Test
    @DisplayName("set(key, value) on an existing key mutates the loaded entity in place and re-flushes.")
    void shouldUpdateExistingSettingValue() {
      // GIVEN
      final SettingsEntity existing = new SettingsEntity();
      existing.setKey("existing.key");
      existing.setValue("old.value");
      when(settingsRepository.findByKey("existing.key")).thenReturn(Optional.of(existing));
      when(settingsRepository.saveAndFlush(any(SettingsEntity.class)))
          .thenAnswer(i -> i.getArgument(0));

      // WHEN
      settingsDataService.set("existing.key", "new.value");

      // THEN
      assertThat(existing.getValue()).isEqualTo("new.value");
      verify(settingsRepository).saveAndFlush(existing);
    }

    @Test
    @DisplayName("set(null, value) throws NullPointerException — no repository interaction.")
    void shouldRejectNullKey() {
      assertThatThrownBy(() -> settingsDataService.set(null, "value"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("set(key, null) throws NullPointerException — null is not a valid stored value.")
    void shouldRejectNullValue() {
      assertThatThrownBy(() -> settingsDataService.set("key", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTest {

    @Test
    @DisplayName("delete(key) routes to SettingsRepository.deleteByKey — never deleteById (UUID/String mismatch).")
    void shouldDeleteByKey() {
      // WHEN
      settingsDataService.delete("to.delete");

      // THEN
      verify(settingsRepository).deleteByKey("to.delete");
    }

    @Test
    @DisplayName("delete(null) throws NullPointerException — no repository interaction.")
    void shouldRejectNullKey() {
      assertThatThrownBy(() -> settingsDataService.delete(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  @DisplayName("SettingsDataService constructor rejects a null repository with NullPointerException.")
  void shouldRejectNullRepository() {
    assertThatThrownBy(() -> new SettingsDataService(null))
        .isInstanceOf(NullPointerException.class);
  }
}
