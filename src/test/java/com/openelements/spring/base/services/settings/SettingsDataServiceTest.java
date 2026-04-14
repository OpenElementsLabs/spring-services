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
 * Unit test for {@link SettingsDataService}.
 *
 * <p>Note: Integration test with real DB is not possible because {@link SettingsRepository}
 * declares {@code String} as ID type while {@link SettingsEntity} inherits {@code UUID} from {@code
 * AbstractEntity}. This type mismatch causes {@code InvalidDataAccessApiUsageException} at runtime.
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
    void shouldReturnValueWhenKeyExists() {
      // GIVEN
      final SettingsEntity entity = new SettingsEntity();
      entity.setKey("app.name");
      entity.setValue("TestApp");
      when(settingsRepository.findById("app.name")).thenReturn(Optional.of(entity));

      // WHEN
      final Optional<String> result = settingsDataService.get("app.name");

      // THEN
      assertThat(result).isPresent().contains("TestApp");
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
      // GIVEN
      when(settingsRepository.findById("nonexistent")).thenReturn(Optional.empty());

      // WHEN
      final Optional<String> result = settingsDataService.get("nonexistent");

      // THEN
      assertThat(result).isEmpty();
    }

    @Test
    void shouldRejectNullKey() {
      assertThatThrownBy(() -> settingsDataService.get(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("set")
  class SetTest {

    @Test
    void shouldCreateNewSettingWhenKeyDoesNotExist() {
      // GIVEN
      when(settingsRepository.findById("new.key")).thenReturn(Optional.empty());
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

    @Test
    void shouldUpdateExistingSettingValue() {
      // GIVEN
      final SettingsEntity existing = new SettingsEntity();
      existing.setKey("existing.key");
      existing.setValue("old.value");
      when(settingsRepository.findById("existing.key")).thenReturn(Optional.of(existing));
      when(settingsRepository.saveAndFlush(any(SettingsEntity.class)))
          .thenAnswer(i -> i.getArgument(0));

      // WHEN
      settingsDataService.set("existing.key", "new.value");

      // THEN
      assertThat(existing.getValue()).isEqualTo("new.value");
      verify(settingsRepository).saveAndFlush(existing);
    }

    @Test
    void shouldRejectNullKey() {
      assertThatThrownBy(() -> settingsDataService.set(null, "value"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullValue() {
      assertThatThrownBy(() -> settingsDataService.set("key", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTest {

    @Test
    void shouldDeleteByKey() {
      // WHEN
      settingsDataService.delete("to.delete");

      // THEN
      verify(settingsRepository).deleteById("to.delete");
    }

    @Test
    void shouldRejectNullKey() {
      assertThatThrownBy(() -> settingsDataService.delete(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  void shouldRejectNullRepository() {
    assertThatThrownBy(() -> new SettingsDataService(null))
        .isInstanceOf(NullPointerException.class);
  }
}
