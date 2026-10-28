package com.openelements.spring.base.services.altcha;

import java.time.Instant;
import java.util.Objects;
import org.altcha.altcha.v2.Altcha;
import org.jspecify.annotations.Nullable;

/**
 * Creates ALTCHA challenges and verifies the solutions sent by the widget: signature, expiry, solution
 * and that the challenge was not used before.
 *
 * <p>Endpoints use {@link AltchaService}, which turns a failed verification into an exception. This
 * class is the lower level for code that wants the {@link VerificationResult} itself.
 */
public class AltchaVerifier {

    static final String ALGORITHM = "PBKDF2/SHA-256";

    private final AltchaProperties properties;
    private final String hmacKey;
    private final ReplayStore replayStore;

    /**
     * Creates the verifier.
     *
     * @param properties  the configuration
     * @param replayStore the store that remembers used challenges
     * @throws IllegalStateException if no HMAC key of at least {@value AltchaProperties#MIN_KEY_LENGTH}
     *                               characters is configured, so that start-up fails
     */
    public AltchaVerifier(final AltchaProperties properties, final ReplayStore replayStore) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.replayStore = Objects.requireNonNull(replayStore, "replayStore must not be null");
        final String key = properties.hmacKey();
        if (key == null || key.isBlank() || key.length() < AltchaProperties.MIN_KEY_LENGTH) {
            throw new IllegalStateException("openelements.altcha.hmac-key must be set to a secret of at least "
                    + AltchaProperties.MIN_KEY_LENGTH + " characters (or remove openelements.altcha.enabled=true)");
        }
        this.hmacKey = key;
    }

    /**
     * Creates a new signed challenge.
     *
     * @return the challenge as JSON for the widget
     */
    public String createChallenge() {
        try {
            return Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                    .algorithm(ALGORITHM)
                    .cost(properties.cost())
                    .keyPrefixLength(properties.keyPrefixLength())
                    .hmacSignatureSecret(hmacKey)
                    .expiresInSeconds(properties.expires().toSeconds())).toJson();
        } catch (final Exception e) {
            throw new IllegalStateException("Can not create an ALTCHA challenge", e);
        }
    }

    /**
     * Verifies the payload of the widget (form field {@code altcha}). A verified challenge is marked as
     * used.
     *
     * @param payload the Base64 payload, or {@code null}
     * @return the result; never throws for invalid input
     */
    public VerificationResult verify(final @Nullable String payload) {
        if (payload == null || payload.isBlank()) {
            return VerificationResult.MISSING;
        }
        final Altcha.Payload parsed;
        final Altcha.VerifySolutionResult result;
        try {
            parsed = Altcha.parsePayload(payload.trim());
            result = Altcha.verifySolution(parsed.challenge(), parsed.solution(), hmacKey, Altcha.kdf(ALGORITHM));
        } catch (final Exception e) {
            return VerificationResult.INVALID;
        }
        if (result.expired()) {
            return VerificationResult.EXPIRED;
        }
        if (!result.verified()) {
            return VerificationResult.INVALID;
        }
        final Number expiresAt = parsed.challenge().parameters().expiresAt();
        final Instant keepUntil = expiresAt != null ? Instant.ofEpochSecond(expiresAt.longValue())
                : Instant.now().plus(properties.expires());
        return replayStore.markUsed(parsed.challenge().signature(), keepUntil) ? VerificationResult.VERIFIED
                : VerificationResult.REUSED;
    }

    /** The outcome of {@link #verify(String)}. */
    public enum VerificationResult {

        /** The payload carries a valid solution of an unused challenge. */
        VERIFIED,

        /** No payload: the widget did not run or the field is missing. */
        MISSING,

        /** Not parseable, wrong signature or wrong solution. */
        INVALID,

        /** The challenge has expired. */
        EXPIRED,

        /** The challenge was already used. */
        REUSED;

        /**
         * Returns whether this is the successful outcome.
         *
         * @return {@code true} for {@link #VERIFIED}
         */
        public boolean verified() {
            return this == VERIFIED;
        }
    }
}
