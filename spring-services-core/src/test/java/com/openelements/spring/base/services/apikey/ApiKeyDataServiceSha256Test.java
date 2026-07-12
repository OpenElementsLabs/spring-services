package com.openelements.spring.base.services.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ApiKeyDataService#sha256Hex(String)} helper.
 *
 * <h2>What is tested</h2>
 *
 * <p>The contract of the static hashing helper used to fingerprint raw API keys before
 * persistence:
 *
 * <ol>
 *   <li>The output matches the canonical SHA-256 digest for a known input ({@code "hello"}) —
 *       guards against accidental algorithm changes, encoding bugs, or stray byte-order flips.
 *   <li>The function is deterministic — hashing the same input twice yields the same digest, so
 *       authentication-by-rehash is stable across calls.
 *   <li>Different inputs map to different digests — the trivial collision-avoidance baseline.
 *   <li>The output is always a 64-character lower-case hex string — the {@code keyHash} column
 *       schema depends on this length and casing.
 *   <li>The empty string hashes to the canonical SHA-256 of the empty input — documents that
 *       the helper does no input pre-processing.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with AssertJ. The helper is a pure function with no collaborators; the tests
 * call it directly with string literals and compare against pre-computed expected values.
 *
 * <p><b>Mock-Audit.</b> Zero mocks.
 */
@DisplayName("ApiKeyDataService SHA-256 hashing")
class ApiKeyDataServiceSha256Test {

  @Test
  @DisplayName(
      "sha256Hex(\"hello\") matches the canonical SHA-256 digest — guards against silent algorithm changes.")
  void shouldProduceCorrectSha256ForKnownInput() {
    // GIVEN
    final String input = "hello";

    // WHEN
    final String hash = ApiKeyDataService.sha256Hex(input);

    // THEN
    // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
    assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }

  @Test
  @DisplayName("sha256Hex is deterministic — hashing the same input twice produces identical digests.")
  void shouldProduceConsistentHashForSameInput() {
    // GIVEN
    final String input = "crm_abc123";

    // WHEN
    final String hash1 = ApiKeyDataService.sha256Hex(input);
    final String hash2 = ApiKeyDataService.sha256Hex(input);

    // THEN
    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  @DisplayName("Different inputs map to different digests — the baseline collision-avoidance check.")
  void shouldProduceDifferentHashesForDifferentInputs() {
    // WHEN
    final String hash1 = ApiKeyDataService.sha256Hex("key1");
    final String hash2 = ApiKeyDataService.sha256Hex("key2");

    // THEN
    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  @DisplayName("sha256Hex output is always 64 lower-case hex characters — matches the keyHash column schema.")
  void shouldReturn64CharHexString() {
    // WHEN
    final String hash = ApiKeyDataService.sha256Hex("test");

    // THEN
    assertThat(hash).hasSize(64);
    assertThat(hash).matches("[0-9a-f]{64}");
  }

  @Test
  @DisplayName(
      "sha256Hex(\"\") returns the canonical SHA-256 of empty input — no implicit padding or pre-processing.")
  void shouldHandleEmptyString() {
    // WHEN
    final String hash = ApiKeyDataService.sha256Hex("");

    // THEN
    // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
    assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
