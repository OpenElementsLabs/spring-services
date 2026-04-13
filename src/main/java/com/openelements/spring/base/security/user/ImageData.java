package com.openelements.spring.base.security.user;

/**
 * Holds binary image data together with its MIME content type.
 *
 * @param data        the image bytes
 * @param contentType the MIME content type (e.g. "image/png")
 */
public record ImageData(byte[] data, String contentType) {

    /**
     * Maximum image size in bytes (2 MB). Used as the JPA column length for image fields
     * and must match the Spring multipart {@code max-file-size} configured in application.yml.
     */
    public static final int MAX_IMAGE_SIZE = 2 * 1024 * 1024;
}
