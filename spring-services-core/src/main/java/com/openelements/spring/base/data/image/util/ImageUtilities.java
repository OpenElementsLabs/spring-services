package com.openelements.spring.base.data.image.util;

import com.openelements.spring.base.data.image.ImageData;
import com.openelements.spring.base.data.image.ImageType;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Utility methods for converting and resizing images into JPEG form. */
public class ImageUtilities {

  /** Utility class; not meant to be instantiated. */
  private ImageUtilities() {}

  /**
   * Converts the given image to JPEG.
   *
   * @param data the source image
   * @return a new image holding the JPEG-encoded data
   */
  public static ImageData toJpeg(@NonNull ImageData data) {
    return toJpeg(data.data(), data.contentType());
  }

  /**
   * Converts the given raw image bytes to JPEG.
   *
   * @param data the source image bytes
   * @param contentType the MIME content type of the source image
   * @return a new image holding the JPEG-encoded data
   */
  public static ImageData toJpeg(@NonNull byte[] data, @NonNull String contentType) {
    Objects.requireNonNull(data, "data must not be null");
    Objects.requireNonNull(contentType, "contentType must not be null");
    final ImageType type = ImageType.fromContentType(contentType);
    return switch (type) {
      case JPEG -> new ImageData(data, ImageType.JPEG);
      case PNG -> new ImageData(ImageTranscoder.pngToJpeg(data), ImageType.JPEG);
      case WEBP -> new ImageData(ImageTranscoder.webpToJpeg(data), ImageType.JPEG);
      case HEIC, HEIF -> new ImageData(ImageTranscoder.heicToJpeg(data), ImageType.JPEG);
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image type accepted");
    };
  }

  /**
   * Converts the given image to JPEG and scales it to fit within the given bounds.
   *
   * @param data the source image
   * @param maxWidth the maximum width in pixels
   * @param maxHeight the maximum height in pixels
   * @return a new image holding the scaled JPEG-encoded data
   */
  public static ImageData toJpeg(@NonNull ImageData data, int maxWidth, int maxHeight) {
    return toJpeg(data.data(), data.contentType(), maxWidth, maxHeight);
  }

  /**
   * Converts the given raw image bytes to JPEG and scales the result to fit within the given bounds.
   *
   * @param data the source image bytes
   * @param contentType the MIME content type of the source image
   * @param maxWidth the maximum width in pixels
   * @param maxHeight the maximum height in pixels
   * @return a new image holding the scaled JPEG-encoded data
   */
  public static ImageData toJpeg(
      @NonNull byte[] data, @NonNull String contentType, int maxWidth, int maxHeight) {
    Objects.requireNonNull(data, "data must not be null");
    Objects.requireNonNull(contentType, "contentType must not be null");
    final ImageType type = ImageType.fromContentType(contentType);
    final ImageData jpegData =
        switch (type) {
          case JPEG -> new ImageData(data, ImageType.JPEG);
          case PNG -> new ImageData(ImageTranscoder.pngToJpeg(data), ImageType.JPEG);
          case WEBP -> new ImageData(ImageTranscoder.webpToJpeg(data), ImageType.JPEG);
          case HEIC, HEIF -> new ImageData(ImageTranscoder.heicToJpeg(data), ImageType.JPEG);
          default ->
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image type accepted");
        };
    return new ImageData(
        ImageTranscoder.resizeJpeg(jpegData.data(), maxWidth, maxHeight), ImageType.JPEG);
  }
}
