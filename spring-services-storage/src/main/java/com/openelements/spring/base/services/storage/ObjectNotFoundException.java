package com.openelements.spring.base.services.storage;

/** Thrown when an object is read but no object exists under the key. */
public class ObjectNotFoundException extends RuntimeException {

    /**
     * Creates an exception naming the key that has no object.
     *
     * @param key the key that was read
     */
    public ObjectNotFoundException(String key) {
        super("No object found for key: " + key);
    }

    /**
     * Creates an exception naming the key that has no object, keeping the store's own report of the
     * miss as the cause.
     *
     * @param key   the key that was read
     * @param cause the store's exception reporting the missing object
     */
    public ObjectNotFoundException(String key, Throwable cause) {
        super("No object found for key: " + key, cause);
    }
}
