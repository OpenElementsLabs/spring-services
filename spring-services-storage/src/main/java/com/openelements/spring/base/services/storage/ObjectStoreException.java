package com.openelements.spring.base.services.storage;

import org.jspecify.annotations.Nullable;

/** Thrown when an object-store operation fails for a reason other than a missing object. */
public class ObjectStoreException extends RuntimeException {

    /**
     * Creates an exception describing a failed operation.
     *
     * @param message what the store could not do
     * @param cause   the underlying failure, or {@code null} where the store itself is the origin
     */
    public ObjectStoreException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
