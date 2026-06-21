package com.openelements.spring.base.services.webhook.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end CRUD integration tests for {@link WebhookDataService} against a real Postgres
 * database.
 *
 * <h2>What is tested</h2>
 *
 * <p>The full lifecycle of a webhook registration through the service layer: create with
 * auto-generated id, update (URL change and active-flag toggle), delete, single-row fetch by id,
 * list-all, paginated listing, and the {@code findAllActive()} filter that the {@code
 * WebhookSender} relies on to fan out events only to live endpoints. Negative cases (update /
 * delete with an unknown {@code UUID}) are pinned to {@link IllegalArgumentException} — the
 * contract surface that REST controllers translate to HTTP 404.
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. {@link WebhookDataService} and {@link
 * WebhookRepository} are real beans; the per-test {@code @BeforeEach} truncates the {@code
 * webhook} table to keep scenarios independent.
 *
 * <p><b>Mock-Audit.</b> One {@code @MockBean} of {@link AuthService}. The service inherits from
 * {@code AbstractDbBackedDataService}, whose audit hooks consult {@code AuthService} for the
 * acting principal — outside a real HTTP request there is no JWT to resolve, so the bean is
 * mocked to default-stub behaviour. No other collaborator is mocked: every assertion is made
 * against rows actually persisted to Postgres.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("WebhookDataService Integration Tests")
class WebhookDataServiceIntegrationTest {

  @Autowired private WebhookDataService webhookDataService;

  @Autowired private WebhookRepository webhookRepository;

  @MockBean private AuthService authService;

  @BeforeEach
  void setUp() {
    webhookRepository.deleteAll();
  }

  @Nested
  @DisplayName("save")
  class SaveTest {

    @Test
    @DisplayName("save(...) with a null id persists a new webhook and returns a DTO with a generated UUID.")
    void shouldCreateNewWebhook() {
      // GIVEN
      final WebhookDto dto = new WebhookDto(null, "https://example.com/hook", true, null, null);

      // WHEN
      final WebhookDto saved = webhookDataService.save(dto);

      // THEN
      assertThat(saved.id()).isNotNull();
      assertThat(saved.url()).isEqualTo("https://example.com/hook");
      assertThat(saved.active()).isTrue();
    }
  }

  @Nested
  @DisplayName("findAllActive")
  class FindAllActiveTest {

    @Test
    @DisplayName("findAllActive() returns only rows with active=true — the filter WebhookSender relies on to skip disabled endpoints.")
    void shouldReturnOnlyActiveWebhooks() {
      // GIVEN
      webhookDataService.save(new WebhookDto(null, "https://active1.com/hook", true, null, null));
      webhookDataService.save(new WebhookDto(null, "https://active2.com/hook", true, null, null));
      webhookDataService.save(new WebhookDto(null, "https://inactive.com/hook", false, null, null));

      // WHEN
      final List<WebhookDto> active = webhookDataService.findAllActive();

      // THEN
      assertThat(active).hasSize(2);
      assertThat(active).allMatch(WebhookDto::active);
      assertThat(active)
          .extracting(WebhookDto::url)
          .containsExactlyInAnyOrder("https://active1.com/hook", "https://active2.com/hook");
    }

    @Test
    @DisplayName("findAllActive() returns an empty list when every persisted webhook has active=false.")
    void shouldReturnEmptyListWhenNoActiveWebhooks() {
      // GIVEN
      webhookDataService.save(new WebhookDto(null, "https://inactive.com/hook", false, null, null));

      // WHEN
      final List<WebhookDto> active = webhookDataService.findAllActive();

      // THEN
      assertThat(active).isEmpty();
    }
  }

  @Nested
  @DisplayName("getAll")
  class GetAllTest {

    @Test
    @DisplayName("getAll() returns every persisted webhook regardless of the active flag.")
    void shouldReturnAllWebhooks() {
      // GIVEN
      webhookDataService.save(new WebhookDto(null, "https://hook1.com", true, null, null));
      webhookDataService.save(new WebhookDto(null, "https://hook2.com", false, null, null));

      // WHEN
      final List<WebhookDto> all = webhookDataService.getAll();

      // THEN
      assertThat(all).hasSize(2);
    }
  }

  @Nested
  @DisplayName("save (update)")
  class SaveUpdateTest {

    @Test
    @DisplayName("save(...) with a known id updates the URL in place — id is preserved.")
    void shouldUpdateExistingWebhookUrl() {
      // GIVEN
      final WebhookDto saved =
          webhookDataService.save(new WebhookDto(null, "https://old.com/hook", true, null, null));

      // WHEN
      final WebhookDto updated =
          webhookDataService.save(
              new WebhookDto(saved.id(), "https://new.com/hook", true, null, null));

      // THEN
      assertThat(updated.id()).isEqualTo(saved.id());
      assertThat(updated.url()).isEqualTo("https://new.com/hook");
    }

    @Test
    @DisplayName("Toggling active=false through save(...) persists the change — findAllActive() will subsequently exclude this row.")
    void shouldDeactivateWebhook() {
      // GIVEN
      final WebhookDto saved =
          webhookDataService.save(
              new WebhookDto(null, "https://example.com/hook", true, null, null));

      // WHEN
      final WebhookDto updated =
          webhookDataService.save(
              new WebhookDto(saved.id(), "https://example.com/hook", false, null, null));

      // THEN
      assertThat(updated.active()).isFalse();
    }

    @Test
    @DisplayName("save(...) with an unknown id raises IllegalArgumentException — no row is silently inserted with the caller-supplied UUID.")
    void shouldThrowForNonExistentId() {
      final WebhookDto dto =
          new WebhookDto(UUID.randomUUID(), "https://example.com/hook", true, null, null);
      assertThatThrownBy(() -> webhookDataService.save(dto))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTest {

    @Test
    @DisplayName("delete(...) removes the row — a subsequent findById returns Optional.empty().")
    void shouldDeleteExistingWebhook() {
      // GIVEN
      final WebhookDto saved =
          webhookDataService.save(
              new WebhookDto(null, "https://example.com/hook", true, null, null));

      // WHEN
      webhookDataService.delete(saved);

      // THEN
      assertThat(webhookDataService.findById(saved.id())).isEmpty();
    }

    @Test
    @DisplayName("delete(...) with an unknown id raises IllegalArgumentException — a stale REST DELETE never silently no-ops.")
    void shouldThrowWhenDeletingNonExistentWebhook() {
      final WebhookDto dto =
          new WebhookDto(UUID.randomUUID(), "https://example.com/hook", true, null, null);
      assertThatThrownBy(() -> webhookDataService.delete(dto))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("findAll (paginated)")
  class FindAllPaginatedTest {

    @Test
    @DisplayName("findAll(PageRequest.of(0, 20)) over 25 rows returns 20 items, totalElements=25, totalPages=2.")
    void shouldReturnPaginatedResults() {
      // GIVEN
      for (int i = 0; i < 25; i++) {
        webhookDataService.save(
            new WebhookDto(null, "https://example.com/hook" + i, true, null, null));
      }

      // WHEN
      final Page<WebhookDto> page = webhookDataService.findAll(PageRequest.of(0, 20));

      // THEN
      assertThat(page.getContent()).hasSize(20);
      assertThat(page.getTotalElements()).isEqualTo(25);
      assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("findAll(...) on an empty table returns an empty Page with totalElements=0.")
    void shouldReturnEmptyPageWhenNoWebhooks() {
      // WHEN
      final Page<WebhookDto> page = webhookDataService.findAll(PageRequest.of(0, 20));

      // THEN
      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }
  }

  @Nested
  @DisplayName("findById")
  class FindByIdTest {

    @Test
    @DisplayName("findById(existingId) returns an Optional with the matching DTO.")
    void shouldFindExistingWebhook() {
      // GIVEN
      final WebhookDto saved =
          webhookDataService.save(
              new WebhookDto(null, "https://find-me.com/hook", true, null, null));

      // WHEN
      final Optional<WebhookDto> found = webhookDataService.findById(saved.id());

      // THEN
      assertThat(found).isPresent();
      assertThat(found.get().url()).isEqualTo("https://find-me.com/hook");
    }

    @Test
    @DisplayName("findById(unknownId) returns Optional.empty() — no exception, the caller gets a clean miss.")
    void shouldReturnEmptyForNonExistentId() {
      // WHEN
      final Optional<WebhookDto> found = webhookDataService.findById(UUID.randomUUID());

      // THEN
      assertThat(found).isEmpty();
    }
  }
}
