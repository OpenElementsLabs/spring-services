package com.openelements.spring.services.apikey;

import com.openelements.spring.services.security.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling API key generation, hashing, CRUD, and authentication lookup.
 */
@Service
@Transactional
public class ApiKeyService {

    private static final String KEY_PREFIX = "crm_";
    private static final int KEY_RANDOM_LENGTH = 48;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserService userService;

    public ApiKeyService(final ApiKeyRepository apiKeyRepository, final UserService userService) {
        this.apiKeyRepository = Objects.requireNonNull(apiKeyRepository, "apiKeyRepository must not be null");
        this.userService = Objects.requireNonNull(userService, "userService must not be null");
    }

    static String sha256Hex(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(64);
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Creates a new API key. The raw key is returned only in this response.
     *
     * @param request the create request with a name
     * @return the created key DTO including the raw key (shown once)
     */
    public ApiKeyCreatedDto create(final ApiKeyCreateDto request) {
        Objects.requireNonNull(request, "request must not be null");

        final String rawKey = generateRawKey();
        final String keyHash = sha256Hex(rawKey);
        final String keyPrefix = buildPrefix(rawKey);
        final String createdBy = userService.getCurrentUser().name();

        final ApiKeyEntity entity = new ApiKeyEntity();
        entity.setName(request.name());
        entity.setKeyHash(keyHash);
        entity.setKeyPrefix(keyPrefix);
        entity.setCreatedBy(createdBy);
        final ApiKeyEntity saved = apiKeyRepository.saveAndFlush(entity);

        return new ApiKeyCreatedDto(
                saved.getId(),
                saved.getName(),
                saved.getKeyPrefix(),
                rawKey,
                saved.getCreatedBy(),
                saved.getCreatedAt()
        );
    }

    /**
     * Lists API keys with pagination.
     *
     * @param pageable pagination parameters
     * @return a page of API key DTOs (without raw keys)
     */
    @Transactional(readOnly = true)
    public Page<ApiKeyDto> list(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable must not be null");
        return apiKeyRepository.findAll(pageable).map(ApiKeyDto::fromEntity);
    }

    /**
     * Deletes an API key.
     *
     * @param id the API key ID
     * @throws ResponseStatusException with 404 if not found
     */
    public void delete(final UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        final ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found: " + id));
        apiKeyRepository.delete(entity);
    }

    /**
     * Authenticates a raw API key by hashing and looking up in the database.
     *
     * @param rawKey the raw key from the X-API-Key header
     * @return the entity if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<ApiKeyEntity> authenticate(final String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        final String keyHash = sha256Hex(rawKey);
        return apiKeyRepository.findByKeyHash(keyHash);
    }

    private String generateRawKey() {
        final StringBuilder sb = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < KEY_RANDOM_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private String buildPrefix(final String rawKey) {
        return rawKey.substring(0, 8) + "..." + rawKey.substring(rawKey.length() - 4);
    }
}
