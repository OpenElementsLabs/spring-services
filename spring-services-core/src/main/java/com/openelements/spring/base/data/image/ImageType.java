package com.openelements.spring.base.data.image;

import org.springframework.http.MediaType;

/** Supported image formats, each paired with its MIME content type. */
public enum ImageType {
  /** Portable Network Graphics ({@code image/png}). */
  PNG("image/png"),
  /** JPEG ({@code image/jpeg}). */
  JPEG("image/jpeg"),
  /** WebP ({@code image/webp}). */
  WEBP("image/webp"),
  /** High Efficiency Image Coding ({@code image/heic}). */
  HEIC("image/heic"),
  /** High Efficiency Image File format ({@code image/heif}). */
  HEIF("image/heif"),
  /** Scalable Vector Graphics ({@code image/svg+xml}). */
  SVG("image/svg+xml");

  private final String contentType;

  ImageType(String contentType) {
    this.contentType = contentType;
  }

  /**
   * Resolves the image type matching the given MIME content type.
   *
   * @param contentType the MIME content type to resolve
   * @return the matching image type
   * @throws IllegalArgumentException if no image type matches the content type
   */
  public static ImageType fromContentType(String contentType) {
    for (ImageType type : ImageType.values()) {
      if (type.contentType.equals(contentType)) {
        return type;
      }
    }
    throw new IllegalArgumentException(contentType + " is not a valid image content type");
  }

  /**
   * Returns the MIME content type associated with this image type.
   *
   * @return the MIME content type (e.g. "image/png")
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Returns this image type's content type as a Spring {@link MediaType}.
   *
   * @return the media type for this image type
   */
  public MediaType toMediaType() {
    return MediaType.parseMediaType(contentType);
  }
}
