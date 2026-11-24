package com.openelements.spring.base.services.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SlackSignatureVerifier")
class SlackSignatureVerifierTest {

  private static final String SECRET = "test-signing-secret";

  private static final long NOW = 1_700_000_000L;

  private static final byte[] BODY =
      "{\"type\":\"event_callback\",\"event_id\":\"Ev1\"}".getBytes(StandardCharsets.UTF_8);

  private final SlackSignatureVerifier verifier =
      new SlackSignatureVerifier(SECRET, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

  @Test
  @DisplayName("Accepts Slack's published example request")
  void acceptsSlackExample() {
    // From https://api.slack.com/authentication/verifying-requests-from-slack
    final String secret = "8f742231b10e8888abcd99yyyzzz85a5";
    final long timestamp = 1531420618L;
    final byte[] body =
        ("token=xyzz0WbapA4vBCDEFasx0q6G&team_id=T1DC2JH3J&team_domain=testteamnow"
                + "&channel_id=G8PSS9T3V&channel_name=foobar&user_id=U2CERLKJA"
                + "&user_name=roadrunner&command=%2Fwebhook-collect&text="
                + "&response_url=https%3A%2F%2Fhooks.slack.com%2Fcommands%2FT1DC2JH3J"
                + "%2F397700885554%2F96rGlfmibIGlgcZRskXaIFfN"
                + "&trigger_id=398738663015.47445629121.803a0bc887a14d10d2c447fce8b6703c")
            .getBytes(StandardCharsets.UTF_8);
    final SlackSignatureVerifier exampleVerifier =
        new SlackSignatureVerifier(
            secret, Clock.fixed(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC));

    assertThat(
            exampleVerifier.verify(
                Long.toString(timestamp),
                "v0=a2114d57b48eac39b9ad189dd8316235a7b4a8d21a10bd27519666489c69b503",
                body))
        .isTrue();
  }

  @Test
  @DisplayName("Accepts a body signed with the configured secret and a current timestamp")
  void acceptsValidSignature() {
    assertThat(verifier.verify(Long.toString(NOW), sign(SECRET, NOW, BODY), BODY)).isTrue();
  }

  @Test
  @DisplayName("Rejects a body signed with another secret")
  void rejectsWrongSecret() {
    assertThat(verifier.verify(Long.toString(NOW), sign("other-secret", NOW, BODY), BODY))
        .isFalse();
  }

  @Test
  @DisplayName("Rejects a body changed by one byte after signing")
  void rejectsTamperedBody() {
    final String signature = sign(SECRET, NOW, BODY);
    final byte[] tampered = BODY.clone();
    tampered[tampered.length - 2] = '2';

    assertThat(verifier.verify(Long.toString(NOW), signature, tampered)).isFalse();
  }

  @Test
  @DisplayName("Accepts a timestamp exactly five minutes in the past or in the future")
  void acceptsTimestampAtBoundary() {
    final long past = NOW - 300;
    final long future = NOW + 300;

    assertThat(verifier.verify(Long.toString(past), sign(SECRET, past, BODY), BODY)).isTrue();
    assertThat(verifier.verify(Long.toString(future), sign(SECRET, future, BODY), BODY)).isTrue();
  }

  @Test
  @DisplayName("Rejects a validly signed timestamp more than five minutes in the past")
  void rejectsStaleTimestamp() {
    final long stale = NOW - 301;

    assertThat(verifier.verify(Long.toString(stale), sign(SECRET, stale, BODY), BODY)).isFalse();
  }

  @Test
  @DisplayName("Rejects a validly signed timestamp more than five minutes in the future")
  void rejectsFutureTimestamp() {
    final long future = NOW + 301;

    assertThat(verifier.verify(Long.toString(future), sign(SECRET, future, BODY), BODY))
        .isFalse();
  }

  @Test
  @DisplayName("Rejects a request without signature header")
  void rejectsMissingSignature() {
    assertThat(verifier.verify(Long.toString(NOW), null, BODY)).isFalse();
  }

  @Test
  @DisplayName("Rejects a request without timestamp header")
  void rejectsMissingTimestamp() {
    assertThat(verifier.verify(null, sign(SECRET, NOW, BODY), BODY)).isFalse();
  }

  @Test
  @DisplayName("Rejects a timestamp that is not a number")
  void rejectsNonNumericTimestamp() {
    assertThat(verifier.verify("yesterday", sign(SECRET, NOW, BODY), BODY)).isFalse();
  }

  @Test
  @DisplayName("Rejects a correct digest without the v0= prefix")
  void rejectsSignatureWithoutVersionPrefix() {
    final String digestOnly = sign(SECRET, NOW, BODY).substring("v0=".length());

    assertThat(verifier.verify(Long.toString(NOW), digestOnly, BODY)).isFalse();
  }

  @Test
  @DisplayName("Refuses a blank signing secret")
  void refusesBlankSecret() {
    assertThatThrownBy(() -> new SlackSignatureVerifier(" ", Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static String sign(final String secret, final long timestamp, final byte[] body) {
    return SlackRequestSigner.sign(secret, timestamp, body);
  }
}
