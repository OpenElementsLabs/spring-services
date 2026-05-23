package com.openelements.spring.base.data.image.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;

public class HeicSupportCheck {

    private static final Logger log = LoggerFactory.getLogger(HeicSupportCheck.class);
    private static final String PROBE_RESOURCE = "/heic-probe/sample.heic";

    public static boolean verifyHeicSupport() {
        final boolean readerRegistered = Arrays.stream(ImageIO.getReaderFormatNames())
                .anyMatch(n -> n.equalsIgnoreCase("heif") || n.equalsIgnoreCase("heic"));
        if (!readerRegistered) {
            log.warn("HEIC: no ImageIO reader registered. Check JDK 21+ and the imageio-heif dependency.");
            return false;
        }
        try (InputStream sample = HeicSupportCheck.class.getResourceAsStream(PROBE_RESOURCE)) {
            if (sample == null) {
                log.error("HEIC: probe sample {} is missing on classpath.",
                        PROBE_RESOURCE);
                return false;
            }
            final BufferedImage img = ImageIO.read(sample);
            if (img != null) {
                log.info("HEIC: support verified (decoded {}x{} probe image).",
                        img.getWidth(), img.getHeight());
                return true;
            } else {
                log.error("HEIC: reader registered but decode returned null. "
                        + "Install libheif1 + libheif-plugin-libde265 in the runtime image.");
            }
        } catch (final UnsatisfiedLinkError | Exception e) {
            log.error("HEIC: support check failed. Install libheif1 + "
                    + "libheif-plugin-libde265 in the runtime image.", e);
        }
        return false;
    }
}
