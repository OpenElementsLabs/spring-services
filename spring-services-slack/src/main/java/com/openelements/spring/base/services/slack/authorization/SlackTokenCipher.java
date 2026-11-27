package com.openelements.spring.base.services.slack.authorization;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts Slack user tokens for storage with AES-256-GCM.
 *
 * <p>Every encryption uses a fresh random 12-byte IV; the result is {@code IV || ciphertext+tag}.
 * The workspace and user id are bound as associated data, so a ciphertext copied to the row of
 * another user does not decrypt. {@link #decrypt} never throws for bad input — a wrong key, a
 * tampered or truncated ciphertext all come back as empty.
 */
public final class SlackTokenCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";

  private static final int IV_LENGTH = 12;

  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;

  private final SecureRandom random = new SecureRandom();

  /**
   * Creates the cipher.
   *
   * @param key the AES key, exactly {@value SlackUserAuthorizationProperties#KEY_LENGTH} bytes; the
   *     array is copied
   * @throws IllegalArgumentException if the key has another length
   */
  public SlackTokenCipher(final byte[] key) {
    Objects.requireNonNull(key, "key must not be null");
    if (key.length != SlackUserAuthorizationProperties.KEY_LENGTH) {
      throw new IllegalArgumentException(
          "key must be " + SlackUserAuthorizationProperties.KEY_LENGTH + " bytes");
    }
    this.key = new SecretKeySpec(Arrays.copyOf(key, key.length), "AES");
  }

  /**
   * Encrypts a token for one user of one workspace.
   *
   * @param token the user token
   * @param teamId the workspace the token belongs to
   * @param userId the user the token belongs to
   * @return {@code IV || ciphertext+tag}
   */
  public byte[] encrypt(final String token, final String teamId, final String userId) {
    Objects.requireNonNull(token, "token must not be null");
    final byte[] iv = new byte[IV_LENGTH];
    random.nextBytes(iv);
    try {
      final Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, teamId, userId);
      final byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(IV_LENGTH + encrypted.length).put(iv).put(encrypted).array();
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM is not available", e);
    }
  }

  /**
   * Decrypts a token that was encrypted for the same user of the same workspace.
   *
   * @param data {@code IV || ciphertext+tag}
   * @param teamId the workspace the token must belong to
   * @param userId the user the token must belong to
   * @return the token, or empty if the data cannot be decrypted with this key for this user
   */
  public Optional<String> decrypt(final byte[] data, final String teamId, final String userId) {
    Objects.requireNonNull(data, "data must not be null");
    if (data.length <= IV_LENGTH) {
      return Optional.empty();
    }
    try {
      final Cipher cipher =
          cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(data, IV_LENGTH), teamId, userId);
      final byte[] token = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
      return Optional.of(new String(token, StandardCharsets.UTF_8));
    } catch (final AEADBadTagException e) {
      return Optional.empty();
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM is not available", e);
    }
  }

  private Cipher cipher(final int mode, final byte[] iv, final String teamId, final String userId)
      throws GeneralSecurityException {
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
    // NUL separates the ids, so ("T1", "U12") and ("T1U", "12") are different associated data.
    cipher.updateAAD((teamId + '\0' + userId).getBytes(StandardCharsets.UTF_8));
    return cipher;
  }
}
