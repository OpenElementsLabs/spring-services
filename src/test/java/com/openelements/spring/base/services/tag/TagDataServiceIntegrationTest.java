package com.openelements.spring.base.services.tag;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link TagDataService} using Testcontainers with PostgreSQL.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("TagDataService Integration Tests")
class TagDataServiceIntegrationTest {

    @Autowired
    private TagDataService tagDataService;

    @Autowired
    private TagRepository tagRepository;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tagRepository.deleteAll();
    }

    @Nested
    @DisplayName("save (create)")
    class SaveCreateTest {

        @Test
        void shouldCreateNewTag() {
            //GIVEN
            final TagDto dto = new TagDto(UUID.randomUUID(), "Important", "High priority items", "#FF0000");

            //WHEN
            final TagDto saved = tagDataService.save(dto);

            //THEN
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
            //WHEN
            final List<TagDto> tags = tagDataService.getAll();

            //THEN
            assertThat(tags).isEmpty();
        }

        @Test
        void shouldReturnAllTags() {
            //GIVEN
            tagDataService.save(new TagDto(UUID.randomUUID(), "Tag1", "Desc1", "#000001"));
            tagDataService.save(new TagDto(UUID.randomUUID(), "Tag2", "Desc2", "#000002"));

            //WHEN
            final List<TagDto> tags = tagDataService.getAll();

            //THEN
            assertThat(tags).hasSize(2);
            assertThat(tags).extracting(TagDto::name).containsExactlyInAnyOrder("Tag1", "Tag2");
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTest {

        @Test
        void shouldFindExistingTag() {
            //GIVEN
            final TagDto saved = tagDataService.save(new TagDto(UUID.randomUUID(), "Find Me", "Desc", "#AABBCC"));

            //WHEN
            final Optional<TagDto> found = tagDataService.findById(saved.id());

            //THEN
            assertThat(found).isPresent();
            assertThat(found.get().name()).isEqualTo("Find Me");
        }

        @Test
        void shouldReturnEmptyForNonExistentId() {
            //WHEN
            final Optional<TagDto> found = tagDataService.findById(UUID.randomUUID());

            //THEN
            assertThat(found).isEmpty();
        }
    }
}