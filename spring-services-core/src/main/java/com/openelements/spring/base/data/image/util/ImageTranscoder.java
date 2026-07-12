package com.openelements.spring.base.data.image.util;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Decodes PNG/WebP/HEIC bytes, applies EXIF orientation where the source carries it, flattens any
 * alpha channel over a white background, and re-encodes as JPEG. The output never carries an alpha
 * channel and never preserves the source's textual chunks or ICC profile — re-encoding is the
 * metadata strip.
 */
final class ImageTranscoder {

  private static final Color BACKGROUND = Color.WHITE;
  private static final float JPEG_QUALITY = 0.9f;
  private static final String JPEG_FORMAT = "jpeg";
  private static final String WEBP_FORMAT = "webp";
  private static final String HEIF_FORMAT = "heif";
  private static final String HEIC_FORMAT = "heic";

  private static final AtomicBoolean heicSupportChecked = new AtomicBoolean();

  private static final AtomicBoolean heicSupported = new AtomicBoolean();

  private ImageTranscoder() {}

  public static byte[] pngToJpeg(final byte[] pngBytes) {
    Objects.requireNonNull(pngBytes, "pngBytes must not be null");
    final BufferedImage source = readImage(pngBytes, "PNG");
    final BufferedImage flattened = flattenOnWhite(source);
    return encodeJpeg(flattened);
  }

  public static byte[] webpToJpeg(final byte[] webpBytes) {
    Objects.requireNonNull(webpBytes, "webpBytes must not be null");
    final OrientedImage decoded =
        readImageWithOrientation(webpBytes, new String[] {WEBP_FORMAT}, "WebP");
    final BufferedImage upright = applyExifOrientation(decoded.image, decoded.orientation);
    final BufferedImage flattened = flattenOnWhite(upright);
    return encodeJpeg(flattened);
  }

  public static byte[] resizeJpeg(final byte[] jpegBytes, final int maxWidth, final int maxHeight) {
    Objects.requireNonNull(jpegBytes, "jpegBytes must not be null");
    if (maxWidth <= 0 || maxHeight <= 0) {
      throw new IllegalArgumentException("maxWidth and maxHeight must be positive");
    }
    final BufferedImage source = readImage(jpegBytes, "JPEG");
    final int srcWidth = source.getWidth();
    final int srcHeight = source.getHeight();
    if (srcWidth <= maxWidth && srcHeight <= maxHeight) {
      return jpegBytes;
    }
    final double scale = Math.min((double) maxWidth / srcWidth, (double) maxHeight / srcHeight);
    final int targetWidth = Math.max(1, (int) Math.round(srcWidth * scale));
    final int targetHeight = Math.max(1, (int) Math.round(srcHeight * scale));
    final BufferedImage resized =
        new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    final Graphics2D g = resized.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
    } finally {
      g.dispose();
    }
    return encodeJpeg(resized);
  }

  public static byte[] heicToJpeg(final byte[] heicBytes) {
    Objects.requireNonNull(heicBytes, "heicBytes must not be null");

    if (heicSupportChecked.compareAndSet(false, true)) {
      heicSupported.set(HeicSupportCheck.verifyHeicSupport());
    }

    // NightMonkeys may register the reader under either name; the probe in
    // HeicSupportCheck accepts both, so the transcoder must too.
    final OrientedImage decoded =
        readImageWithOrientation(heicBytes, new String[] {HEIF_FORMAT, HEIC_FORMAT}, "HEIC");
    final BufferedImage upright = applyExifOrientation(decoded.image, decoded.orientation);
    // HEIC files are always opaque — no alpha channel to flatten — but
    // routing through the same pipeline keeps the encoder happy and is
    // cheap for already-opaque images.
    final BufferedImage flattened = flattenOnWhite(upright);
    return encodeJpeg(flattened);
  }

  private static BufferedImage readImage(final byte[] bytes, final String label) {
    final BufferedImage source;
    try {
      source = ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (final IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode " + label, e);
    }
    if (source == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode " + label);
    }
    return source;
  }

  /**
   * Reads via the explicit reader pipeline so we can inspect the source metadata and extract EXIF
   * orientation. Falls back to orientation 1 (normal) when no orientation hint is present. The
   * {@code formatNames} are tried in order — the first one with a registered reader wins.
   */
  private static OrientedImage readImageWithOrientation(
      final byte[] bytes, final String[] formatNames, final String label) {
    ImageReader reader = null;
    for (final String formatName : formatNames) {
      final Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
      if (readers.hasNext()) {
        reader = readers.next();
        break;
      }
    }
    if (reader == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode " + label);
    }
    try (final ImageInputStream iis =
        ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (iis == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode " + label);
      }
      reader.setInput(iis, true, false);
      final BufferedImage image = reader.read(0);
      if (image == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode " + label);
      }
      int orientation = 1;
      try {
        final IIOMetadata md = reader.getImageMetadata(0);
        if (md != null) {
          orientation = extractExifOrientation(md);
        }
      } catch (final IOException | RuntimeException ignored) {
        // Best-effort: many decoders surface orientation only via the
        // standard metadata tree, which not all formats populate.
        // Default to 1 = no rotation rather than failing the upload.
      }
      return new OrientedImage(image, orientation);
    } catch (final IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode " + label, e);
    } finally {
      reader.dispose();
    }
  }

  private static int extractExifOrientation(final IIOMetadata md) {
    // Walk the standard IIOMetadata tree looking for an "ImageOrientation"
    // attribute. TwelveMonkeys exposes EXIF orientation via this tree for
    // formats that carry an EXIF chunk; NightMonkeys does likewise for
    // HEIC's primary image.
    for (final String formatName : md.getMetadataFormatNames()) {
      final Node tree = md.getAsTree(formatName);
      final Integer found = searchOrientation(tree);
      if (found != null) {
        return found;
      }
    }
    return 1;
  }

  private static Integer searchOrientation(final Node node) {
    if (node == null) {
      return null;
    }
    if (node instanceof IIOMetadataNode metaNode) {
      // Standard tree attribute name used by ImageIO.
      final String value = metaNode.getAttribute("value");
      if ("ImageOrientation".equals(metaNode.getNodeName()) && !value.isEmpty()) {
        return mapStandardOrientation(value);
      }
      // EXIF-style numeric tag.
      if ("Orientation".equalsIgnoreCase(metaNode.getNodeName()) && !value.isEmpty()) {
        try {
          return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
          // Fall through; try children.
        }
      }
    }
    final NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Integer found = searchOrientation(children.item(i));
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static int mapStandardOrientation(final String value) {
    // ImageIO's standard tree uses textual names that describe the
    // rotation/flip required to display the stored pixels correctly. EXIF
    // orientation tags use the inverse convention: orientation 6 means the
    // stored image is rotated 90° clockwise, so display correction is 90°
    // CCW ("Rotate90" in standard-tree terms). The mapping below converts
    // standard-tree → EXIF orientation values so applyExifOrientation can
    // produce an upright image. The orientation 6/8 happy-path tests are
    // @Disabled pending fixtures; the mapping should be re-verified once
    // those tests land.
    return switch (value) {
      case "Normal" -> 1;
      case "FlipH" -> 2;
      case "Rotate180" -> 3;
      case "FlipV" -> 4;
      case "FlipHRotate90" -> 5;
      case "Rotate90" -> 6;
      case "FlipVRotate90" -> 7;
      case "Rotate270" -> 8;
      default -> 1;
    };
  }

  /**
   * Applies an EXIF orientation tag (1..8) to the given image. Orientation 1 is the identity. The
   * transforms swap dimensions for the rotated variants (5..8) so callers don't have to.
   */
  static BufferedImage applyExifOrientation(final BufferedImage src, final int orientation) {
    if (orientation <= 1 || orientation > 8) {
      return src;
    }
    final int w = src.getWidth();
    final int h = src.getHeight();
    final AffineTransform tx = new AffineTransform();
    final int targetWidth;
    final int targetHeight;
    switch (orientation) {
      case 2 -> {
        tx.scale(-1.0, 1.0);
        tx.translate(-w, 0);
        targetWidth = w;
        targetHeight = h;
      }
      case 3 -> {
        tx.translate(w, h);
        tx.rotate(Math.PI);
        targetWidth = w;
        targetHeight = h;
      }
      case 4 -> {
        tx.scale(1.0, -1.0);
        tx.translate(0, -h);
        targetWidth = w;
        targetHeight = h;
      }
      case 5 -> {
        tx.rotate(Math.PI / 2);
        tx.scale(1.0, -1.0);
        targetWidth = h;
        targetHeight = w;
      }
      case 6 -> {
        tx.translate(h, 0);
        tx.rotate(Math.PI / 2);
        targetWidth = h;
        targetHeight = w;
      }
      case 7 -> {
        tx.scale(-1.0, 1.0);
        tx.translate(-h, 0);
        tx.translate(0, w);
        tx.rotate(3 * Math.PI / 2);
        targetWidth = h;
        targetHeight = w;
      }
      case 8 -> {
        tx.translate(0, w);
        tx.rotate(3 * Math.PI / 2);
        targetWidth = h;
        targetHeight = w;
      }
      default -> {
        return src;
      }
    }
    final BufferedImage dest =
        new BufferedImage(
            targetWidth,
            targetHeight,
            src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);
    final AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
    op.filter(src, dest);
    return dest;
  }

  private static BufferedImage flattenOnWhite(final BufferedImage source) {
    final BufferedImage flattened =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    final Graphics2D g = flattened.createGraphics();
    try {
      g.setColor(BACKGROUND);
      g.fillRect(0, 0, flattened.getWidth(), flattened.getHeight());
      g.drawImage(source, 0, 0, null);
    } finally {
      g.dispose();
    }
    return flattened;
  }

  private static byte[] encodeJpeg(final BufferedImage flattened) {
    final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(JPEG_FORMAT);
    if (!writers.hasNext()) {
      throw new IllegalStateException("No JPEG ImageWriter available in this JDK");
    }
    final ImageWriter writer = writers.next();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      final ImageWriteParam params = writer.getDefaultWriteParam();
      params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      params.setCompressionQuality(JPEG_QUALITY);
      try (final MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(flattened, null, null), params);
      } catch (final IOException e) {
        throw new IllegalStateException("Failed to encode JPEG", e);
      }
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private record OrientedImage(BufferedImage image, int orientation) {}
}
