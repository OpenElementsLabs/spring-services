package com.openelements.spring.base.services.altcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.altcha.altcha.v2.Altcha;
import org.junit.jupiter.api.Test;

class AltchaVerifierTest {

    private static final String KEY = "unit-test-altcha-key-with-at-least-32-characters";

    private final AltchaVerifier verifier = new AltchaVerifier(properties(KEY), new InMemoryReplayStore());

    @Test
    void acceptsASolvedChallengeOnce() throws Exception {
        final String payload = AltchaSolver.solve(verifier.createChallenge());
        assertThat(verifier.verify(payload)).isEqualTo(AltchaVerifier.VerificationResult.VERIFIED);
        assertThat(verifier.verify(payload)).isEqualTo(AltchaVerifier.VerificationResult.REUSED);
    }

    @Test
    void rejectsMissingInvalidForeignAndExpiredPayloads() throws Exception {
        assertThat(verifier.verify(null)).isEqualTo(AltchaVerifier.VerificationResult.MISSING);
        assertThat(verifier.verify(" ")).isEqualTo(AltchaVerifier.VerificationResult.MISSING);
        assertThat(verifier.verify("bm8gYWx0Y2hh")).isEqualTo(AltchaVerifier.VerificationResult.INVALID);
        assertThat(verifier.verify(AltchaSolver.solve(challenge("another-key-with-at-least-32-characters-x",
                Instant.now().plusSeconds(60))))).isEqualTo(AltchaVerifier.VerificationResult.INVALID);
        assertThat(verifier.verify(AltchaSolver.solve(challenge(KEY, Instant.now().minusSeconds(60)))))
                .isEqualTo(AltchaVerifier.VerificationResult.EXPIRED);
    }

    @Test
    void requiresAnHmacKey() {
        for (String key : new String[]{null, " ", "too-short"}) {
            assertThatThrownBy(() -> new AltchaVerifier(properties(key), new InMemoryReplayStore()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("openelements.altcha.hmac-key");
        }
    }

    @Test
    void replayStoreForgetsExpiredChallenges() {
        final InMemoryReplayStore store = new InMemoryReplayStore();
        assertThat(store.markUsed("a", Instant.now().minusSeconds(1))).isTrue();
        assertThat(store.markUsed("b", Instant.now().plusSeconds(60))).isTrue();
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.markUsed("b", Instant.now().plusSeconds(60))).isFalse();
    }

    private static Altcha.Challenge challenge(final String key, final Instant expiresAt) throws Exception {
        return Altcha.createChallenge(new Altcha.CreateChallengeOptions().algorithm("PBKDF2/SHA-256").cost(1)
                .keyPrefixLength(1).hmacSignatureSecret(key).expiresAt(expiresAt.getEpochSecond()));
    }

    static AltchaProperties properties(final String key) {
        return new AltchaProperties(true, key, Duration.ofMinutes(10), 1, 1, AltchaProperties.DEFAULT_CHALLENGE_PATH);
    }
}
