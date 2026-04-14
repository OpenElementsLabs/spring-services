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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration test for {@link TagDataService} using Testcontainers with PostgreSQL. */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("TagDataService Integration Tests")
class TagDataServiceIntegrationTest {

  @Autowired private TagDataService tagDataService;

  @Autowired private TagRepository tagRepository;

  @MockBean private AuthService authService;

  @BeforeEach
  void setUp() {
    tagRepository.deleteAll();
  }

  @Nested
  @DisplayName("save (create)")
  class SaveCreateTest {

    @Test
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
    void shouldReturnEmptyListWhenNoTags() {
      // WHEN
      final List<TagDto> tags = tagDataService.getAll();

      // THEN
      assertThat(tags).isEmpty();
    }

    @Test
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
    void shouldDeleteExistingTag() {
      // GIVEN
      final TagDto saved = tagDataService.save(new TagDto(null, "To Delete", "Desc", "#FF0000"));

      // WHEN
      tagDataService.delete(saved);

      // THEN
      assertThat(tagDataService.findById(saved.id())).isEmpty();
    }

    @Test
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
    void shouldReturnEmptyForNonExistentId() {
      // WHEN
      final Optional<TagDto> found = tagDataService.findById(UUID.randomUUID());

      // THEN
      assertThat(found).isEmpty();
    }
  }
}
