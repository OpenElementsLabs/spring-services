package com.openelements.spring.services.keys;

import com.openelements.spring.services.data.WithId;
import java.util.Objects;
import java.util.UUID;

public record KeyData(UUID id, String principal, String key) implements WithId {

    public KeyData {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(key, "key must not be null");
    }

    public KeyData(String principal, String key) {
        this(null, principal, key);
    }

    public KeyData(WithApiKey withApiKey) {
        this(null, Objects.requireNonNull(withApiKey, "withApiKey must not be null").getPrincipal(), withApiKey.getApiKey());
    }
}
