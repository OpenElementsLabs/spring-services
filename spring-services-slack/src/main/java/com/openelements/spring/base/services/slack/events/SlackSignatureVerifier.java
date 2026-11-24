package com.openelements.spring.base.services.slack.events;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Verifies that a request was sent by Slack, following <a
 * href="https://api.slack.com/authentication/verifying-requests-from-slack">Verifying requests from
 * Slack</a>.
 *
 * <p>Slack signs every request with the app's signing secret: the {@code X-Slack-Signature} header
 * is {@code v0=} followed by the hex-encoded HMAC-SHA256 of {@code v0:<timestamp>:<raw body>}, where
 * the timestamp is the {@code X-Slack-Request-Timestamp} header. A request is accepted only if this
 * signature matches and the timestamp lies within {@link #TOLERANCE} of the clock, which keeps a
 * recorded request from being replayed later.
 *
 * <p>The signature is computed over the raw bytes as received, so callers must verify before they
 * parse the body. Signatures are compared in constant time.
 */
public final class SlackSignatureVerifier {

  /** Maximum distance between the request timestamp and the clock, in either direction. */
  public static final Duration TOLERANCE = Duration.ofMinutes(5);

  private static final String ALGORITHM = "HmacSHA256";

  private static final String VERSION = "v0";

  private final SecretKeySpec key;

  private final Clock clock;

  /**
   * Creates a verifier for the given signing secret.
   *
   * @param signingSecret the signing secret of the Slack app
   * @param clock the clock the request timestamp is compared with
   * @throws IllegalArgumentException if {@code signingSecret} is blank
   */
  public SlackSignatureVerifier(final String signingSecret, final Clock clock) {
    Objects.requireNonNull(signingSecret, "signingSecret must not be null");
    if (signingSecret.isBlank()) {
      throw new IllegalArgumentException("signingSecret must not be blank");
    }
    this.key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Checks the signature of a request.
   *
   * @param timestamp the value of the {@code X-Slack-Request-Timestamp} header, if present
   * @param signature the value of the {@code X-Slack-Signature} header, if present
   * @param rawBody the request body exactly as received
   * @return {@code true} if both headers are present, the timestamp is recent and the signature
   *     matches
   */
  public boolean verify(
      final @Nullable String timestamp, final @Nullable String signature, final byte[] rawBody) {
    Objects.requireNonNull(rawBody, "rawBody must not be null");
    if (timestamp == null || signature == null || !isRecent(timestamp)) {
      return false;
    }
    final byte[] expected = sign(timestamp, rawBody).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
  }

  private boolean isRecent(final String timestamp) {
    final long epochSeconds;
    try {
      epochSeconds = Long.parseLong(timestamp);
    } catch (final NumberFormatException e) {
      return false;
    }
    final long distance = Math.abs(clock.instant().getEpochSecond() - epochSeconds);
    return distance <= TOLERANCE.toSeconds();
  }

  private String sign(final String timestamp, final byte[] rawBody) {
    final Mac mac = newMac();
    mac.update((VERSION + ":" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
    return VERSION + "=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
  }

  private Mac newMac() {
    try {
      final Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return mac;
    } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 is not available", e);
    }
  }
}
