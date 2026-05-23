package com.openelements.spring.base.data.image;

import com.openelements.spring.base.data.image.util.ImageUtilities;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

/**
 * Holds binary image data together with its MIME content type.
 *
 * @param data      the image bytes
 * @param imageType the MIME content type (e.g. "image/png")
 */
public record ImageData(@NonNull byte[] data, @NonNull ImageType imageType) {

    /**
     * Maximum image size in bytes (2 MB). Used as the JPA column length for image fields and must
     * match the Spring multipart {@code max-file-size} configured in application.yml.
     */
    public static final int MAX_IMAGE_SIZE = 2 * 1024 * 1024;

    public ImageData {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(imageType, "imageType must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException("data must not be empty");
        }
        if (data.length > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("File too large (max 2MB)");
        }
    }

    public static ImageData of(@NonNull byte[] data, @NonNull String contentType) {
        Objects.requireNonNull(contentType, "contentType must not be null");
        return new ImageData(data, ImageType.fromContentType(contentType));
    }

    public static ImageData of(@NonNull MultipartFile file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        return of(file.getBytes(), file.getContentType());
    }

    public String contentType() {
        return imageType.getContentType();
    }

    public MediaType mediaType() {
        return imageType.toMediaType();
    }

    public ResponseEntity<byte[]> toHttpResponse() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType())).body(data());
    }

    public ImageData asJpeg() {
        return ImageUtilities.toJpeg(this);
    }

    public ImageData asJpeg(int maxWidth, int maxHeight) {
        return ImageUtilities.toJpeg(this, maxWidth, maxHeight);
    }
}
