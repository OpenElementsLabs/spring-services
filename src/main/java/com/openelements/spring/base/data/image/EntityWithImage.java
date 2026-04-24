package com.openelements.spring.base.data.image;

import com.openelements.spring.base.data.DbEntity;
import java.util.Optional;

public interface EntityWithImage extends DbEntity {

  byte[] getRawImageData();

  void setRawImageData(byte[] rawImageData);

  String getContentType();

  void setContentType(String contentType);

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

  default ImageType getImageType() {
    return Optional.ofNullable(getContentType())
        .map(c -> ImageType.fromContentType(c))
        .orElse(null);
  }

  default void setImageType(ImageType imageType) {
    final String contentType =
        Optional.ofNullable(imageType).map(t -> t.getContentType()).orElse(null);
    setContentType(contentType);
  }

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
