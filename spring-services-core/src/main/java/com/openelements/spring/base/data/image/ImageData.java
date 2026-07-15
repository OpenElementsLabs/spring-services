package com.openelements.spring.base.data.image;

import com.openelements.spring.base.data.image.util.ImageUtilities;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * Holds binary image data together with its MIME content type.
 *
 * @param data the image bytes
 * @param imageType the MIME content type (e.g. "image/png")
 */
public record ImageData(@NonNull byte[] data, @NonNull ImageType imageType) {

  /**
   * Maximum image size in bytes (20 MB). Used as the JPA column length for image fields and must
   * match the Spring multipart {@code max-file-size} configured in application.yml.
   */
  public static final int MAX_IMAGE_SIZE = 20 * 1024 * 1024;

  /**
   * Validates the components, rejecting {@code null}, empty, and oversized image data.
   *
   * @throws IllegalArgumentException if {@code data} is empty or larger than {@link #MAX_IMAGE_SIZE}
   */
  public ImageData {
    Objects.requireNonNull(data, "data must not be null");
    Objects.requireNonNull(imageType, "imageType must not be null");
    if (data.length == 0) {
      throw new IllegalArgumentException("data must not be empty");
    }
    if (data.length > MAX_IMAGE_SIZE) {
      throw new IllegalArgumentException("File too large (max 20 MB)");
    }
  }

  /**
   * Creates an instance from raw bytes and a MIME content type string.
   *
   * @param data the image bytes
   * @param contentType the MIME content type (e.g. "image/png")
   * @return the image data
   */
  public static ImageData of(@NonNull byte[] data, @NonNull String contentType) {
    Objects.requireNonNull(contentType, "contentType must not be null");
    return new ImageData(data, ImageType.fromContentType(contentType));
  }

  /**
   * Creates an instance from an uploaded multipart file.
   *
   * @param file the uploaded file
   * @return the image data
   * @throws IOException if the file bytes cannot be read
   */
  public static ImageData of(@NonNull MultipartFile file) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    return of(file.getBytes(), file.getContentType());
  }

  /**
   * Returns the MIME content type of this image.
   *
   * @return the content type (e.g. "image/png")
   */
  public String contentType() {
    return imageType.getContentType();
  }

  /**
   * Returns the content type of this image as a Spring {@link MediaType}.
   *
   * @return the media type of this image
   */
  public MediaType mediaType() {
    return imageType.toMediaType();
  }

  /**
   * Wraps this image in a {@code 200 OK} HTTP response with the appropriate content type.
   *
   * @return an HTTP response carrying the image bytes
   */
  public ResponseEntity<byte[]> toHttpResponse() {
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType())).body(data());
  }

  /**
   * Converts this image to JPEG.
   *
   * @return a new instance holding the JPEG-encoded image
   */
  public ImageData asJpeg() {
    return ImageUtilities.toJpeg(this);
  }

  /**
   * Converts this image to JPEG, scaling it to fit within the given bounds.
   *
   * @param maxWidth the maximum width in pixels
   * @param maxHeight the maximum height in pixels
   * @return a new instance holding the scaled JPEG-encoded image
   */
  public ImageData asJpeg(int maxWidth, int maxHeight) {
    return ImageUtilities.toJpeg(this, maxWidth, maxHeight);
  }
}
