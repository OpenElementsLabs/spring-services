package com.openelements.spring.base.services.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * A stored object as listed by {@link ObjectStore#list(String)}.
 *
 * @param key          the object key
 * @param size         the object size in bytes
 * @param lastModified when the object was last written
 */
public record StoredObject(String key, long size, Instant lastModified) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if {@code key} or {@code lastModified} is {@code null}
     * @throws IllegalArgumentException if {@code size} is negative
     */
    public StoredObject {
        Objects.requireNonNull(key, "key must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative, but was " + size);
        }
        Objects.requireNonNull(lastModified, "lastModified must not be null");
    }
}
