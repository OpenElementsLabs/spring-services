package com.openelements.spring.base.services.altcha;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the ALTCHA bot protection, bound from the {@code openelements.altcha.*} namespace.
 *
 * <p>ALTCHA protocol v2 (PBKDF2/SHA-256, matches the widget {@code altcha} 3.x): the browser searches
 * a counter whose derived key starts with a given prefix. There is no {@code maxNumber} as in protocol
 * v1; the difficulty is {@code cost × 16^keyPrefixLength} PBKDF2 iterations on average.
 *
 * <p>{@link #enabled()} defaults to {@code false}. The module needs a secret to sign challenges, and a
 * module that self-activated on classpath presence would fail the start-up of every application that
 * consumes {@code spring-services-all} without configuring one.
 *
 * @param enabled         whether the module is active
 * @param hmacKey         secret for signing the challenges, at least {@value #MIN_KEY_LENGTH}
 *                        characters; required when the module is enabled, start-up fails without it
 * @param expires         how long a challenge can be solved and submitted
 * @param cost            PBKDF2 iterations per attempt
 * @param keyPrefixLength length of the required key prefix in hex characters
 * @param challengePath   path of the endpoint that hands out challenges
 */
@ConfigurationProperties(prefix = "openelements.altcha")
public record AltchaProperties(
        @DefaultValue("false") boolean enabled,
        @Nullable String hmacKey,
        @DefaultValue("PT10M") Duration expires,
        @DefaultValue("5000") int cost,
        @DefaultValue("2") int keyPrefixLength,
        @DefaultValue(AltchaProperties.DEFAULT_CHALLENGE_PATH) String challengePath) {

    /** The path of the challenge endpoint when {@code openelements.altcha.challenge-path} is unset. */
    public static final String DEFAULT_CHALLENGE_PATH = "/api/altcha/challenge";

    /** Minimum length of {@link #hmacKey()}. */
    public static final int MIN_KEY_LENGTH = 32;
}
