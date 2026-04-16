package com.openelements.spring.base.data;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Holds binary image data together with its MIME content type.
 *
 * @param data the image bytes
 * @param contentType the MIME content type (e.g. "image/png")
 */
public record ImageData(@NonNull byte[] data, @NonNull String contentType) {

  /**
   * Maximum image size in bytes (2 MB). Used as the JPA column length for image fields and must
   * match the Spring multipart {@code max-file-size} configured in application.yml.
   */
  public static final int MAX_IMAGE_SIZE = 2 * 1024 * 1024;

  /** SVG content type. */
  public static final String CONTENT_TYPE_SVG = "image/svg+xml";

  /** PNG content types. */
  public static final String CONTENT_TYPE_PNG = "image/png";

  /** JPEG content type. */
  public static final String CONTENT_TYPE_JPEG = "image/jpeg";

  public ImageData {
    Objects.requireNonNull(data, "data must not be null");
    Objects.requireNonNull(contentType, "contentType must not be null");
  }
}
