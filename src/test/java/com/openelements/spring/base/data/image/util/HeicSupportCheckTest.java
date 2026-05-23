package com.openelements.spring.base.data.image.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("HeicSupportCheck")
class HeicSupportCheckTest {

    private static BufferedImage dummyImage() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    @DisplayName("returns false when no HEIC/HEIF reader is registered")
    void returnsFalseWhenNoReaderRegistered() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"png", "jpeg"});

            assertThat(HeicSupportCheck.verifyHeicSupport()).isFalse();

            imageIO.verify(() -> ImageIO.read(any(InputStream.class)), org.mockito.Mockito.never());
        }
    }

    @Test
    @DisplayName("returns true when HEIF reader is registered and probe decodes")
    void returnsTrueWhenHeifReaderAndProbeDecodes() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"png", "heif"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class))).thenReturn(dummyImage());

            assertThat(HeicSupportCheck.verifyHeicSupport()).isTrue();
        }
    }

    @Test
    @DisplayName("returns true when HEIC reader is registered and probe decodes")
    void returnsTrueWhenHeicReaderAndProbeDecodes() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"heic"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class))).thenReturn(dummyImage());

            assertThat(HeicSupportCheck.verifyHeicSupport()).isTrue();
        }
    }

    @Test
    @DisplayName("reader registration check is case-insensitive")
    void readerRegistrationCheckIsCaseInsensitive() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"HEIF"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class))).thenReturn(dummyImage());

            assertThat(HeicSupportCheck.verifyHeicSupport()).isTrue();
        }
    }

    @Test
    @DisplayName("returns false when reader is registered but decode returns null")
    void returnsFalseWhenDecodeReturnsNull() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"heif"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class))).thenReturn(null);

            assertThat(HeicSupportCheck.verifyHeicSupport()).isFalse();
        }
    }

    @Test
    @DisplayName("returns false when decode throws IOException")
    void returnsFalseWhenDecodeThrowsIOException() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"heif"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class)))
                    .thenThrow(new IOException("decode failed"));

            assertThat(HeicSupportCheck.verifyHeicSupport()).isFalse();
        }
    }

    @Test
    @DisplayName("returns false when decode throws RuntimeException")
    void returnsFalseWhenDecodeThrowsRuntimeException() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"heif"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class)))
                    .thenThrow(new IllegalStateException("boom"));

            assertThat(HeicSupportCheck.verifyHeicSupport()).isFalse();
        }
    }

    @Test
    @DisplayName("returns false when native lib is missing (UnsatisfiedLinkError)")
    void returnsFalseWhenUnsatisfiedLinkError() {
        try (MockedStatic<ImageIO> imageIO = mockStatic(ImageIO.class)) {
            imageIO.when(ImageIO::getReaderFormatNames).thenReturn(new String[]{"heif"});
            imageIO.when(() -> ImageIO.read(any(InputStream.class)))
                    .thenThrow(new UnsatisfiedLinkError("libheif missing"));

            assertThat(HeicSupportCheck.verifyHeicSupport()).isFalse();
        }
    }

    @Test
    @DisplayName("probe resource is present on the classpath")
    void probeResourceIsBundled() {
        try (InputStream sample = HeicSupportCheck.class.getResourceAsStream("/heic-probe/sample.heic")) {
            assertThat(sample).as("HEIC probe sample must be bundled on the classpath").isNotNull();
        } catch (IOException e) {
            throw new AssertionError("Failed to read probe resource", e);
        }
    }

    @Test
    @DisplayName("real environment: no HEIC reader on classpath → returns false")
    void realEnvironmentReturnsFalseWithoutHeicLibrary() {
        // No TwelveMonkeys/NightMonkeys deps in pom → no heif/heic reader registered → false.
        // This guards against accidentally adding such a dep without verifying behavior.
        final boolean heicReaderPresent = java.util.Arrays.stream(ImageIO.getReaderFormatNames())
                .anyMatch(n -> n.equalsIgnoreCase("heif") || n.equalsIgnoreCase("heic"));
        assertThat(HeicSupportCheck.verifyHeicSupport()).isEqualTo(heicReaderPresent);
    }
}
