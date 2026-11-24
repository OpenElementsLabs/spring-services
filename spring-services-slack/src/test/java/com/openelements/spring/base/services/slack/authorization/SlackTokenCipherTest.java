package com.openelements.spring.base.services.slack.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SlackTokenCipher")
class SlackTokenCipherTest {

  private static final String TOKEN = "xoxp-1234-5678-abcdef";

  private final SlackTokenCipher cipher = new SlackTokenCipher(key(1));

  @Test
  @DisplayName("Decrypts what it encrypted for the same workspace and user")
  void roundTrip() {
    final byte[] encrypted = cipher.encrypt(TOKEN, "T1", "U1");

    assertThat(cipher.decrypt(encrypted, "T1", "U1")).contains(TOKEN);
  }

  @Test
  @DisplayName("Encrypts the same token differently each time and never in plain text")
  void randomIv() {
    final byte[] first = cipher.encrypt(TOKEN, "T1", "U1");
    final byte[] second = cipher.encrypt(TOKEN, "T1", "U1");

    assertThat(first).isNotEqualTo(second);
    assertThat(new String(first, StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("Does not decrypt with another key")
  void otherKey() {
    final byte[] encrypted = cipher.encrypt(TOKEN, "T1", "U1");

    assertThat(new SlackTokenCipher(key(2)).decrypt(encrypted, "T1", "U1")).isEmpty();
  }

  @Test
  @DisplayName("Does not decrypt for another workspace or another user")
  void boundToRow() {
    final byte[] encrypted = cipher.encrypt(TOKEN, "T1", "U12");

    assertThat(cipher.decrypt(encrypted, "T2", "U12")).isEmpty();
    assertThat(cipher.decrypt(encrypted, "T1", "U2")).isEmpty();
    assertThat(cipher.decrypt(encrypted, "T1U", "12")).isEmpty();
  }

  @Test
  @DisplayName("Does not decrypt a tampered, truncated or empty ciphertext")
  void tampered() {
    final byte[] encrypted = cipher.encrypt(TOKEN, "T1", "U1");
    final byte[] flipped = encrypted.clone();
    flipped[flipped.length - 1] ^= 1;

    assertThat(cipher.decrypt(flipped, "T1", "U1")).isEmpty();
    assertThat(cipher.decrypt(Arrays.copyOf(encrypted, 20), "T1", "U1")).isEmpty();
    assertThat(cipher.decrypt(new byte[0], "T1", "U1")).isEmpty();
  }

  @Test
  @DisplayName("Rejects a key that is not 32 bytes long")
  void rejectsWrongKeyLength() {
    assertThatThrownBy(() -> new SlackTokenCipher(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static byte[] key(final int seed) {
    final byte[] key = new byte[32];
    Arrays.fill(key, (byte) seed);
    return key;
  }
}
