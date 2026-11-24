package com.openelements.spring.base.services.slack.events;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs request bodies the way Slack does, so tests can produce valid and deliberately invalid
 * requests.
 *
 * <p>This repeats the algorithm of {@link SlackSignatureVerifier}. {@code SlackSignatureVerifierTest}
 * checks the verifier against Slack's published example, so both cannot share the same mistake
 * unnoticed.
 */
public final class SlackRequestSigner {

  private SlackRequestSigner() {}

  /**
   * Returns the {@code X-Slack-Signature} value for a body.
   *
   * @param secret the signing secret
   * @param timestamp the {@code X-Slack-Request-Timestamp} value
   * @param body the raw body
   * @return {@code v0=} followed by the hex-encoded HMAC-SHA256
   */
  public static String sign(final String secret, final long timestamp, final byte[] body) {
    try {
      final Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
      return "v0=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
