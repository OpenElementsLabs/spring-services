package com.openelements.spring.base.services.tag;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end CRUD integration tests for {@link TagDataService} against a real Postgres database.
 *
 * <h2>What is tested</h2>
 *
 * <p>The full lifecycle of a tag through the service layer: create with auto-generated id,
 * update existing rows, delete, single-row fetch by id, list-all, and paginated listing. Each
 * mutation roundtrips through Hibernate so the JPA-mapping, the DTO-to-entity mapper and the
 * generated SQL are exercised together. Negative cases (update / delete with an unknown {@code
 * UUID}) are pinned to {@link IllegalArgumentException} — the contract surface that REST
 * controllers translate to HTTP 404.
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. {@link TagDataService} and {@link TagRepository}
 * are real beans; the per-test {@code @BeforeEach} truncates the {@code tag} table to keep
 * scenarios independent.
 *
 * <p><b>Mock-Audit.</b> One {@code @MockitoBean} of {@link AuthService}. The service inherits from
 * {@code AbstractDbBackedDataService}, whose audit hooks consult {@code AuthService} for the
 * acting principal — outside a real HTTP request there is no JWT to resolve, so the bean is
 * mocked to default-stub behaviour. No other collaborator is mocked: every assertion is made
 * against rows actually persisted to Postgres.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("TagDataService Integration Tests")
class TagDataServiceIntegrationTest {

  @Autowired private TagDataService tagDataService;

  @Autowired private TagRepository tagRepository;

  @MockitoBean private AuthService authService;

  @BeforeEach
  void setUp() {
    tagRepository.deleteAll();
  }

  @Nested
  @DisplayName("save (create)")
  class SaveCreateTest {

    @Test
    @DisplayName("save(...) with a null id persists a new tag and returns a DTO with a generated UUID.")
    void shouldCreateNewTag() {
      // GIVEN
      final TagDto dto = new TagDto(null, "Important", "High priority items", "#FF0000");

      // WHEN
      final TagDto saved = tagDataService.save(dto);

      // THEN
      assertThat(saved.id()).isNotNull();
      assertThat(saved.name()).isEqualTo("Important");
      assertThat(saved.description()).isEqualTo("High priority items");
      assertThat(saved.color()).isEqualTo("#FF0000");
    }
  }

  @Nested
  @DisplayName("getAll")
  class GetAllTest {

    @Test
    @DisplayName("getAll() returns an empty list when the tag table is empty.")
    void shouldReturnEmptyListWhenNoTags() {
      // WHEN
      final List<TagDto> tags = tagDataService.getAll();

      // THEN
      assertThat(tags).isEmpty();
    }

    @Test
    @DisplayName("getAll() returns every persisted tag as a DTO.")
    void shouldReturnAllTags() {
      // GIVEN
      tagDataService.save(new TagDto(null, "Tag1", "Desc1", "#000001"));
      tagDataService.save(new TagDto(null, "Tag2", "Desc2", "#000002"));

      // WHEN
      final List<TagDto> tags = tagDataService.getAll();

      // THEN
      assertThat(tags).hasSize(2);
      assertThat(tags).extracting(TagDto::name).containsExactlyInAnyOrder("Tag1", "Tag2");
    }
  }

  @Nested
  @DisplayName("save (update)")
  class SaveUpdateTest {

    @Test
    @DisplayName("save(...) with a known id updates the existing row in place — id is preserved, fields are overwritten.")
    void shouldUpdateExistingTag() {
      // GIVEN
      final TagDto saved = tagDataService.save(new TagDto(null, "Original", "Desc", "#000000"));

      // WHEN
      final TagDto updated =
          tagDataService.save(new TagDto(saved.id(), "Updated", "New Desc", "#FFFFFF"));

      // THEN
      assertThat(updated.id()).isEqualTo(saved.id());
      assertThat(updated.name()).isEqualTo("Updated");
      assertThat(updated.description()).isEqualTo("New Desc");
      assertThat(updated.color()).isEqualTo("#FFFFFF");
    }

    @Test
    @DisplayName("save(...) with an unknown id raises IllegalArgumentException — no row is silently inserted with the caller-supplied UUID.")
    void shouldThrowForNonExistentId() {
      final TagDto dto = new TagDto(UUID.randomUUID(), "Ghost", "Desc", "#000000");
      assertThatThrownBy(() -> tagDataService.save(dto))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTest {

    @Test
    @DisplayName("delete(...) removes the row — a subsequent findById returns Optional.empty().")
    void shouldDeleteExistingTag() {
      // GIVEN
      final TagDto saved = tagDataService.save(new TagDto(null, "To Delete", "Desc", "#FF0000"));

      // WHEN
      tagDataService.delete(saved);

      // THEN
      assertThat(tagDataService.findById(saved.id())).isEmpty();
    }

    @Test
    @DisplayName("delete(...) with an unknown id raises IllegalArgumentException — a stale REST DELETE never silently no-ops.")
    void shouldThrowWhenDeletingNonExistentTag() {
      final TagDto dto = new TagDto(UUID.randomUUID(), "Ghost", "Desc", "#000000");
      assertThatThrownBy(() -> tagDataService.delete(dto))
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
        tagDataService.save(new TagDto(null, "Tag-" + String.format("%02d", i), null, "#000000"));
      }

      // WHEN
      final Page<TagDto> page = tagDataService.findAll(PageRequest.of(0, 20));

      // THEN
      assertThat(page.getContent()).hasSize(20);
      assertThat(page.getTotalElements()).isEqualTo(25);
      assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("findAll(...) on an empty table returns an empty Page with totalElements=0.")
    void shouldReturnEmptyPageWhenNoTags() {
      // WHEN
      final Page<TagDto> page = tagDataService.findAll(PageRequest.of(0, 20));

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
    void shouldFindExistingTag() {
      // GIVEN
      final TagDto saved = tagDataService.save(new TagDto(null, "Find Me", "Desc", "#AABBCC"));

      // WHEN
      final Optional<TagDto> found = tagDataService.findById(saved.id());

      // THEN
      assertThat(found).isPresent();
      assertThat(found.get().name()).isEqualTo("Find Me");
    }

    @Test
    @DisplayName("findById(unknownId) returns Optional.empty() — no exception, the caller gets a clean miss.")
    void shouldReturnEmptyForNonExistentId() {
      // WHEN
      final Optional<TagDto> found = tagDataService.findById(UUID.randomUUID());

      // THEN
      assertThat(found).isEmpty();
    }
  }
}
