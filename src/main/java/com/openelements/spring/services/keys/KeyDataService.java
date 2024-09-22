package com.openelements.spring.services.keys;

import com.openelements.spring.services.tenant.AbstractMultitenantService;
import com.openelements.spring.services.tenant.RepositoryWithTenantSupport;
import com.openelements.spring.services.tenant.TenantService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class KeyDataService extends AbstractMultitenantService<KeyEntity, KeyData> {

    public static final String SHA_256 = "SHA-256";
    private final KeyEntityRepository repository;

    private final TenantService tenantService;

    private final MessageDigest messageDigest;

    public KeyDataService(final KeyEntityRepository repository, final TenantService tenantService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        try {
            messageDigest = MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(SHA_256 + " hashing algorithm not available", e);
        }
    }

    @Override
    protected RepositoryWithTenantSupport<KeyEntity, UUID> getRepository() {
        return repository;
    }

    @Override
    protected TenantService getTenantService() {
        return tenantService;
    }

    @Override
    protected KeyData mapToData(KeyEntity entity) {
        return new KeyData(entity.getId(), entity.getPrincipal(), entity.getKey());
    }

    private String hash(String key) {
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        final byte[] hashedBytes = messageDigest.digest(bytes);
        return new String(hashedBytes, StandardCharsets.UTF_8);
    }

    @Override
    protected KeyEntity updateEntity(KeyEntity entity, KeyData data) {
        entity.setKey(hash(data.key()));
        entity.setPrincipal(data.principal());
        return entity;
    }

    @Override
    protected KeyEntity createNewEntity() {
        return new KeyEntity();
    }

    public Optional<KeyData> findByPrincipal(String userName) {
        return repository.findAllByTenantId(getCurrentTenant())
                .stream()
                .filter(e -> e.getPrincipal().equals(userName))
                .map(this::mapToData)
                .findFirst();
    }

    public boolean isKeyValid(@NonNull final WithApiKey data) {
        Objects.requireNonNull(data, "data must not be null");
        final String hashedKey = hash(data.getApiKey());
        return findByPrincipal(data.getPrincipal())
                .filter(k -> Objects.equals(hashedKey, k.key()))
                .isPresent();
    }
}
