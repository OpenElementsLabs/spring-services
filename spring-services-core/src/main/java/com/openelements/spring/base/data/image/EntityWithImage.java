package com.openelements.spring.base.data.image;

import com.openelements.spring.base.data.DbEntity;
import java.util.Optional;

/**
 * Mixin for entities that carry a single embedded image, stored as raw bytes plus a MIME content
 * type. Provides default methods that bridge between the raw storage columns and the richer {@link
 * ImageData} and {@link ImageType} value types.
 */
public interface EntityWithImage extends DbEntity {

  /**
   * Returns the raw bytes of the stored image.
   *
   * @return the image bytes, or {@code null} if no image is stored
   */
  byte[] getRawImageData();

  /**
   * Sets the raw bytes of the stored image.
   *
   * @param rawImageData the image bytes, or {@code null} to clear the image
   */
  void setRawImageData(byte[] rawImageData);

  /**
   * Returns the MIME content type of the stored image.
   *
   * @return the content type, or {@code null} if no image is stored
   */
  String getContentType();

  /**
   * Sets the MIME content type of the stored image.
   *
   * @param contentType the content type, or {@code null} to clear it
   */
  void setContentType(String contentType);

  /**
   * Returns the stored image as an {@link ImageData} value, if both bytes and type are present.
   *
   * @return the image data, or an empty {@link Optional} if no complete image is stored
   */
  default Optional<ImageData> imageData() {
    final byte[] rawImageData = getRawImageData();
    if (rawImageData == null) {
      return Optional.empty();
    }
    final ImageType imageType = getImageType();
    if (imageType == null) {
      return Optional.empty();
    }
    final ImageData imageData = new ImageData(rawImageData, imageType);
    return Optional.of(imageData);
  }

  /**
   * Returns the {@link ImageType} derived from the stored content type.
   *
   * @return the image type, or {@code null} if no content type is stored
   */
  default ImageType getImageType() {
    return Optional.ofNullable(getContentType())
        .map(c -> ImageType.fromContentType(c))
        .orElse(null);
  }

  /**
   * Sets the stored content type from the given {@link ImageType}.
   *
   * @param imageType the image type, or {@code null} to clear the content type
   */
  default void setImageType(ImageType imageType) {
    final String contentType =
        Optional.ofNullable(imageType).map(t -> t.getContentType()).orElse(null);
    setContentType(contentType);
  }

  /**
   * Stores the given image, updating both the raw bytes and the content type.
   *
   * @param imageData the image to store, or {@code null} to clear the image
   */
  default void setImageData(ImageData imageData) {
    if (imageData == null) {
      setRawImageData(null);
      setContentType(null);
      return;
    }
    final ImageType imageType = imageData.imageType();
    if (imageType == null) {
      throw new IllegalStateException("Image type is null");
    }
    final byte[] rawImageData = imageData.data();
    if (rawImageData == null) {
      throw new IllegalStateException("Image type is null");
    }
    setRawImageData(rawImageData);
    setContentType(imageType.getContentType());
  }
}
