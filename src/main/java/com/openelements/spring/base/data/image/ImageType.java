package com.openelements.spring.base.data.image;

import org.springframework.http.MediaType;

public enum ImageType {
    PNG("image/png"),
    JPEG("image/jpeg"),
    SVG("image/svg+xml");

    private final String contentType;

    ImageType(String contentType) {
        this.contentType = contentType;
    }

    public static ImageType fromContentType(String contentType) {
        for (ImageType type : ImageType.values()) {
            if (type.contentType.equals(contentType)) {
                return type;
            }
        }
        throw new IllegalArgumentException(contentType + " is not a valid image content type");
    }

    public String getContentType() {
        return contentType;
    }

    public MediaType toMediaType() {
        return MediaType.parseMediaType(contentType);
    }
}
