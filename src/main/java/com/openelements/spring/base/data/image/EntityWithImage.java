package com.openelements.spring.base.data.image;

import com.openelements.spring.base.data.DbEntity;

import java.util.Optional;

public interface EntityWithImage extends DbEntity {

    byte[] getRawImageData();

    void setRawImageData(byte[] rawImageData);

    ImageType getImageType();

    void setImageType(ImageType imageType);

    default Optional<ImageData> imageData() {
        final byte[] rawImageData = getRawImageData();
        if (rawImageData == null) {
            return Optional.empty();
        }
        final ImageType imageType = getImageType();
        if (imageType == null) {
            return Optional.empty();
        }
        final ImageData imageData = new ImageData(rawImageData, imageType.getContentType());
        return Optional.of(imageData);
    }

    default void updateImageData(ImageData imageData) {
        if (imageData == null) {
            removeImageData();
            return;
        }
        final ImageType imageType = ImageType.fromContentType(imageData.contentType());
        if (imageType == null) {
            throw new IllegalStateException("Image type is null");
        }
        final byte[] rawImageData = imageData.data();
        if (rawImageData == null) {
            throw new IllegalStateException("Image type is null");
        }
    }

    default void removeImageData() {
        setImageType(null);
        setRawImageData(null);
    }
}
