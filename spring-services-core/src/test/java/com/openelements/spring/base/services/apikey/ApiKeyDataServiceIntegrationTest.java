package com.openelements.spring.base.services.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.time.Instant;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link ApiKeyDataService} against a real Postgres database.
 *
 * <h2>What is tested</h2>
 *
 * <p>The full lifecycle of an API key — creation (raw key handed to the caller once, only the
 * SHA-256 hash and a display-only prefix stored), authentication (matching a raw key against the
 * stored hash and ignoring null/blank/unknown inputs), listing (pagination over persisted rows),
 * and deletion (a 404-mapped {@link ResponseStatusException} for unknown ids). The format of the
 * generated key is locked: it must start with the {@code crm_} prefix, be 4 + 48 characters
 * long, and produce a redacted {@code keyPrefix} of the form {@code crm_XXXX...XXXX}. Two keys
 * created with the same name must be unique. A deleted key must no longer authenticate.
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. The {@link ApiKeyDataService} bean is the real
 * service, and the {@link ApiKeyRepository} is the real Spring Data repository writing to the
 * containerised database.
 *
 * <p><b>Mock-Audit.</b> Two {@code @MockBean}s:
 *
 * <ul>
 *   <li>{@code UserService} — stubbed to return a static "Test Admin" {@code UserDto}. The
 *       service-under-test only reads {@code getCurrentUser().name()} to populate {@code
 *       createdBy} on new keys; a real {@code UserService} would need a JWT-authenticated
 *       Spring Security context, which adds no coverage to the API-key lifecycle.
 *   <li>{@code AuthService} — declared because other Spring components in the test context
 *       wire it transitively; the API-key tests themselves never read from it.
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("ApiKeyDataService Integration Tests")
class ApiKeyDataServiceIntegrationTest {

  @Autowired private ApiKeyDataService apiKeyDataService;

  @Autowired private ApiKeyRepository apiKeyRepository;

  @MockBean private UserService userService;

  @MockBean private AuthService authService;

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
    @DisplayName(
        "create() returns a freshly generated key with the crm_ prefix, 52-char total length, and a redacted display prefix.")
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
    @DisplayName(
        "The raw key is never persisted — only the 64-char SHA-256 hex hash is stored in keyHash.")
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
    @DisplayName(
        "Two create() calls with the same name still produce distinct ids and distinct raw key material.")
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
    @DisplayName(
        "keyPrefix is derived from the raw key as the first 8 chars + '...' + the last 4 chars — safe to display in lists.")
    void shouldBuildKeyPrefixFromRawKey() {
      // WHEN
      final ApiKeyCreatedDto created = apiKeyDataService.create(new ApiKeyCreateDto("Prefix Test"));

      // THEN
      final String key = created.key();
      final String expectedPrefix = key.substring(0, 8) + "..." + key.substring(key.length() - 4);
      assertThat(created.keyPrefix()).isEqualTo(expectedPrefix);
    }

    @Test
    @DisplayName("create() fails fast with NullPointerException when the request DTO is null.")
    void shouldRejectNullRequest() {
      assertThatThrownBy(() -> apiKeyDataService.create(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("authenticate")
  class AuthenticateTest {

    @Test
    @DisplayName(
        "A valid raw key returned by create() authenticates back to the same ApiKeyEntity row.")
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
    @DisplayName("authenticate() returns Optional.empty() for a syntactically plausible key that does not exist.")
    void shouldReturnEmptyForInvalidKey() {
      // WHEN
      final Optional<ApiKeyEntity> result =
          apiKeyDataService.authenticate("crm_invalid_key_that_does_not_exist");

      // THEN
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("authenticate(null) returns Optional.empty() — null is treated as a non-match, not an error.")
    void shouldReturnEmptyForNullKey() {
      assertThat(apiKeyDataService.authenticate(null)).isEmpty();
    }

    @Test
    @DisplayName("authenticate(\"  \") returns Optional.empty() — blank strings never match.")
    void shouldReturnEmptyForBlankKey() {
      assertThat(apiKeyDataService.authenticate("  ")).isEmpty();
    }

    @Test
    @DisplayName("After delete(id), the previously valid raw key no longer authenticates.")
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
    @DisplayName("list() on an empty table returns a Page with zero content and zero totalElements.")
    void shouldReturnEmptyPageWhenNoKeys() {
      // WHEN
      final Page<ApiKeyDto> page = apiKeyDataService.list(PageRequest.of(0, 10));

      // THEN
      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("list() returns every previously-created key as an ApiKeyDto on the first page.")
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
    @DisplayName(
        "list() honours PageRequest size and reports the correct totalElements and totalPages.")
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
    @DisplayName("delete(id) for an existing key removes the row — repository.findById returns empty.")
    void shouldDeleteExistingKey() {
      // GIVEN
      final ApiKeyCreatedDto created = apiKeyDataService.create(new ApiKeyCreateDto("To Delete"));

      // WHEN
      apiKeyDataService.delete(created.id());

      // THEN
      assertThat(apiKeyRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("delete(unknownId) throws ResponseStatusException — surfaces as HTTP 404 to the caller.")
    void shouldThrow404ForNonExistentKey() {
      // GIVEN
      final UUID nonExistentId = UUID.randomUUID();

      // WHEN & THEN
      assertThatThrownBy(() -> apiKeyDataService.delete(nonExistentId))
          .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("delete(null) fails fast with NullPointerException — null is not a valid id.")
    void shouldRejectNullId() {
      assertThatThrownBy(() -> apiKeyDataService.delete(null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
