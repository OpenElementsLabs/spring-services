package com.openelements.spring.base.data.image.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.data.image.ImageData;
import com.openelements.spring.base.data.image.ImageType;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("ImageUtilities")
class ImageUtilitiesTest {

  private static byte[] createJpeg(final int width, final int height) throws Exception {
    return encode(width, height, "jpeg", BufferedImage.TYPE_INT_RGB);
  }

  private static byte[] createPng(final int width, final int height) throws Exception {
    return encode(width, height, "png", BufferedImage.TYPE_INT_ARGB);
  }

  private static byte[] encode(
      final int width, final int height, final String format, final int type) throws Exception {
    final BufferedImage img = new BufferedImage(width, height, type);
    final Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLUE);
      g.fillRect(0, 0, width, height);
    } finally {
      g.dispose();
    }
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!ImageIO.write(img, format, out)) {
      throw new IllegalStateException("No ImageIO writer for " + format);
    }
    return out.toByteArray();
  }

  private static BufferedImage decode(final byte[] bytes) throws Exception {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }

  private static byte[] loadFixture(final String resource) throws Exception {
    try (InputStream in = ImageUtilitiesTest.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource: " + resource);
      }
      return in.readAllBytes();
    }
  }

  private static boolean readerRegistered(final String... formatNames) {
    return Arrays.stream(ImageIO.getReaderFormatNames())
        .anyMatch(
            registered ->
                Arrays.stream(formatNames).anyMatch(wanted -> wanted.equalsIgnoreCase(registered)));
  }

  @Test
  @DisplayName("toJpeg(byte[], String) returns JPEG input unchanged")
  void shouldKeepJpegUnchanged() throws Exception {
    final byte[] jpeg = createJpeg(100, 80);

    final ImageData result = ImageUtilities.toJpeg(jpeg, "image/jpeg");

    assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
    assertThat(result.data()).isSameAs(jpeg);
  }

  @Test
  @DisplayName("toJpeg(byte[], String) converts PNG to JPEG keeping dimensions")
  void shouldConvertPngToJpeg() throws Exception {
    final byte[] png = createPng(120, 90);

    final ImageData result = ImageUtilities.toJpeg(png, "image/png");

    assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(120);
    assertThat(decoded.getHeight()).isEqualTo(90);
  }

  @Test
  @DisplayName("toJpeg(ImageData) delegates to byte[]+contentType overload")
  void shouldAcceptImageDataOverload() throws Exception {
    final ImageData input = new ImageData(createPng(120, 90), ImageType.PNG);

    final ImageData result = ImageUtilities.toJpeg(input);

    assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(120);
    assertThat(decoded.getHeight()).isEqualTo(90);
  }

  @Test
  @DisplayName("toJpeg rejects null data")
  void shouldRejectNullData() {
    assertThatThrownBy(() -> ImageUtilities.toJpeg((byte[]) null, "image/jpeg"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("toJpeg rejects null content type")
  void shouldRejectNullContentType() {
    assertThatThrownBy(() -> ImageUtilities.toJpeg(new byte[] {0x01, 0x02}, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("toJpeg rejects unsupported content type (SVG)")
  void shouldRejectUnsupportedContentType() throws Exception {
    final byte[] bytes = createJpeg(10, 10);

    assertThatThrownBy(() -> ImageUtilities.toJpeg(bytes, "image/svg+xml"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("toJpeg rejects unknown content type")
  void shouldRejectUnknownContentType() throws Exception {
    final byte[] bytes = createJpeg(10, 10);

    assertThatThrownBy(() -> ImageUtilities.toJpeg(bytes, "application/octet-stream"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("resize shrinks landscape image keeping aspect ratio")
  void shouldShrinkLandscapeKeepingAspectRatio() throws Exception {
    final byte[] jpeg = createJpeg(400, 200);

    final ImageData result = ImageUtilities.toJpeg(jpeg, "image/jpeg", 200, 200);

    assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(200);
    assertThat(decoded.getHeight()).isEqualTo(100);
  }

  @Test
  @DisplayName("resize shrinks portrait image keeping aspect ratio")
  void shouldShrinkPortraitKeepingAspectRatio() throws Exception {
    final byte[] jpeg = createJpeg(200, 400);

    final ImageData result = ImageUtilities.toJpeg(jpeg, "image/jpeg", 200, 200);

    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(100);
    assertThat(decoded.getHeight()).isEqualTo(200);
  }

  @Test
  @DisplayName("resize binds on the tighter of the two dimensions")
  void shouldBindOnTighterDimension() throws Exception {
    // 400x200 image, bounds 300x80 → height is the binding dimension (200→80, scale 0.4)
    final byte[] jpeg = createJpeg(400, 200);

    final ImageData result = ImageUtilities.toJpeg(jpeg, "image/jpeg", 300, 80);

    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(160);
    assertThat(decoded.getHeight()).isEqualTo(80);
  }

  @Test
  @DisplayName("resize does not upscale an image already within bounds")
  void shouldNotUpscaleSmallerImage() throws Exception {
    final byte[] jpeg = createJpeg(100, 80);

    final ImageData result = ImageUtilities.toJpeg(jpeg, "image/jpeg", 500, 500);

    // For an already-JPEG input that fits, the same byte[] is reused.
    assertThat(result.data()).isSameAs(jpeg);
    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(100);
    assertThat(decoded.getHeight()).isEqualTo(80);
  }

  @Test
  @DisplayName("resize combined with PNG→JPEG conversion")
  void shouldResizeAndConvertPng() throws Exception {
    final byte[] png = createPng(400, 200);

    final ImageData result = ImageUtilities.toJpeg(png, "image/png", 100, 100);

    assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(100);
    assertThat(decoded.getHeight()).isEqualTo(50);
  }

  @Test
  @DisplayName("toJpeg(ImageData, int, int) overload resizes correctly")
  void shouldResizeViaImageDataOverload() throws Exception {
    final ImageData input = new ImageData(createJpeg(400, 200), ImageType.JPEG);

    final ImageData result = ImageUtilities.toJpeg(input, 200, 200);

    final BufferedImage decoded = decode(result.data());
    assertThat(decoded.getWidth()).isEqualTo(200);
    assertThat(decoded.getHeight()).isEqualTo(100);
  }

  @Test
  @DisplayName("resize rejects non-positive maxWidth")
  void shouldRejectNonPositiveMaxWidth() throws Exception {
    final byte[] jpeg = createJpeg(400, 400);

    assertThatThrownBy(() -> ImageUtilities.toJpeg(jpeg, "image/jpeg", 0, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("resize rejects non-positive maxHeight")
  void shouldRejectNonPositiveMaxHeight() throws Exception {
    final byte[] jpeg = createJpeg(400, 400);

    assertThatThrownBy(() -> ImageUtilities.toJpeg(jpeg, "image/jpeg", 100, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("WebP fixture: converts to JPEG if a reader is registered, otherwise fails with 400")
  void shouldHandleWebpFixture() throws Exception {
    final byte[] webp = loadFixture("/test.webp");

    if (readerRegistered("webp")) {
      // Resize-overload keeps the JPEG output well under ImageData.MAX_IMAGE_SIZE.
      final ImageData result = ImageUtilities.toJpeg(webp, "image/webp", 800, 800);
      assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
      final BufferedImage decoded = decode(result.data());
      assertThat(decoded.getWidth()).isPositive();
      assertThat(decoded.getHeight()).isPositive();
    } else {
      assertThatThrownBy(() -> ImageUtilities.toJpeg(webp, "image/webp"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Could not decode WebP");
    }
  }

  @Test
  @DisplayName("HEIC fixture: converts to JPEG if a reader is registered, otherwise fails with 400")
  void shouldHandleHeicFixture() throws Exception {
    final byte[] heic = loadFixture("/test.HEIC");

    if (readerRegistered("heif", "heic")) {
      final ImageData result = ImageUtilities.toJpeg(heic, "image/heic", 800, 800);
      assertThat(result.imageType()).isEqualTo(ImageType.JPEG);
      final BufferedImage decoded = decode(result.data());
      assertThat(decoded.getWidth()).isPositive();
      assertThat(decoded.getHeight()).isPositive();
    } else {
      assertThatThrownBy(() -> ImageUtilities.toJpeg(heic, "image/heic"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Could not decode HEIC");
    }
  }
}
