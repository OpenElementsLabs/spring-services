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

/** Integration test for {@link WebhookDataService} using Testcontainers with PostgreSQL. */
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
    void shouldReturnEmptyForNonExistentId() {
      // WHEN
      final Optional<WebhookDto> found = webhookDataService.findById(UUID.randomUUID());

      // THEN
      assertThat(found).isEmpty();
    }
  }
}
