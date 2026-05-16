package com.openelements.spring.base.services.apikey;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link ApiKeyDataService} using Testcontainers with PostgreSQL.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("ApiKeyDataService Integration Tests")
class ApiKeyDataServiceIntegrationTest {

    @Autowired
    private ApiKeyDataService apiKeyDataService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();
        when(userService.getCurrentUser())
                .thenReturn(
                        new UserDto(
                                UUID.randomUUID(),
                                "Test Admin",
                                "admin@test.com",
                                null,
                                Instant.now(),
                                Instant.now()));
    }

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        void shouldCreateKeyWithCorrectPrefixAndHash() {
            // GIVEN
            final ApiKeyCreateDto request = new ApiKeyCreateDto("CI Pipeline Key");

            // WHEN
            final ApiKeyCreatedDto created = apiKeyDataService.create(request);

            // THEN
            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("CI Pipeline Key");
            assertThat(created.key()).startsWith("crm_");
            assertThat(created.key()).hasSize(4 + 48); // prefix + random
            assertThat(created.keyPrefix()).contains("...");
            assertThat(created.createdBy()).isEqualTo("Test Admin");
            assertThat(created.createdAt()).isNotNull();
        }

        @Test
        void shouldStoreHashedKeyInDatabase() {
            // GIVEN
            final ApiKeyCreateDto request = new ApiKeyCreateDto("Hashed Key");

            // WHEN
            final ApiKeyCreatedDto created = apiKeyDataService.create(request);

            // THEN
            final Optional<ApiKeyEntity> stored = apiKeyRepository.findById(created.id());
            assertThat(stored).isPresent();
            assertThat(stored.get().getKeyHash()).isNotEqualTo(created.key());
            assertThat(stored.get().getKeyHash()).hasSize(64); // SHA-256 hex
        }

        @Test
        void shouldProduceUniqueKeysForSameName() {
            // GIVEN
            final ApiKeyCreateDto request = new ApiKeyCreateDto("Same Name");

            // WHEN
            final ApiKeyCreatedDto key1 = apiKeyDataService.create(request);
            final ApiKeyCreatedDto key2 = apiKeyDataService.create(request);

            // THEN
            assertThat(key1.id()).isNotEqualTo(key2.id());
            assertThat(key1.key()).isNotEqualTo(key2.key());
        }

        @Test
        void shouldBuildKeyPrefixFromRawKey() {
            // WHEN
            final ApiKeyCreatedDto created = apiKeyDataService.create(new ApiKeyCreateDto("Prefix Test"));

            // THEN
            final String key = created.key();
            final String expectedPrefix = key.substring(0, 8) + "..." + key.substring(key.length() - 4);
            assertThat(created.keyPrefix()).isEqualTo(expectedPrefix);
        }

        @Test
        void shouldRejectNullRequest() {
            assertThatThrownBy(() -> apiKeyDataService.create(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("authenticate")
    class AuthenticateTest {

        @Test
        void shouldAuthenticateWithValidRawKey() {
            // GIVEN
            final ApiKeyCreatedDto created = apiKeyDataService.create(new ApiKeyCreateDto("Auth Key"));

            // WHEN
            final Optional<ApiKeyEntity> result = apiKeyDataService.authenticate(created.key());

            // THEN
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(created.id());
            assertThat(result.get().getName()).isEqualTo("Auth Key");
        }

        @Test
        void shouldReturnEmptyForInvalidKey() {
            // WHEN
            final Optional<ApiKeyEntity> result =
                    apiKeyDataService.authenticate("crm_invalid_key_that_does_not_exist");

            // THEN
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyForNullKey() {
            assertThat(apiKeyDataService.authenticate(null)).isEmpty();
        }

        @Test
        void shouldReturnEmptyForBlankKey() {
            assertThat(apiKeyDataService.authenticate("  ")).isEmpty();
        }

        @Test
        void shouldNotAuthenticateAfterDeletion() {
            // GIVEN
            final ApiKeyCreatedDto created = apiKeyDataService.create(new ApiKeyCreateDto("Deleted Key"));
            apiKeyDataService.delete(created.id());

            // WHEN
            final Optional<ApiKeyEntity> result = apiKeyDataService.authenticate(created.key());

            // THEN
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("list")
    class ListTest {

        @Test
        void shouldReturnEmptyPageWhenNoKeys() {
            // WHEN
            final Page<ApiKeyDto> page = apiKeyDataService.list(PageRequest.of(0, 10));

            // THEN
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        @Test
        void shouldListAllCreatedKeys() {
            // GIVEN
            apiKeyDataService.create(new ApiKeyCreateDto("Key 1"));
            apiKeyDataService.create(new ApiKeyCreateDto("Key 2"));
            apiKeyDataService.create(new ApiKeyCreateDto("Key 3"));

            // WHEN
            final Page<ApiKeyDto> page = apiKeyDataService.list(PageRequest.of(0, 10));

            // THEN
            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getContent())
                    .extracting(ApiKeyDto::name)
                    .containsExactlyInAnyOrder("Key 1", "Key 2", "Key 3");
        }

        @Test
        void shouldSupportPagination() {
            // GIVEN
            for (int i = 0; i < 5; i++) {
                apiKeyDataService.create(new ApiKeyCreateDto("Key " + i));
            }

            // WHEN
            final Page<ApiKeyDto> page = apiKeyDataService.list(PageRequest.of(0, 2));

            // THEN
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        void shouldDeleteExistingKey() {
            // GIVEN
            final ApiKeyCreatedDto created = apiKeyDataService.create(new ApiKeyCreateDto("To Delete"));

            // WHEN
            apiKeyDataService.delete(created.id());

            // THEN
            assertThat(apiKeyRepository.findById(created.id())).isEmpty();
        }

        @Test
        void shouldThrow404ForNonExistentKey() {
            // GIVEN
            final UUID nonExistentId = UUID.randomUUID();

            // WHEN & THEN
            assertThatThrownBy(() -> apiKeyDataService.delete(nonExistentId))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        void shouldRejectNullId() {
            assertThatThrownBy(() -> apiKeyDataService.delete(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
