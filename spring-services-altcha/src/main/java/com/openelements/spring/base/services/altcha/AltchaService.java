package com.openelements.spring.base.services.altcha;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Bot protection for endpoints that accept requests without authentication (forms): an endpoint calls
 * {@link #requireVerified(String)} with the payload of the widget before it processes a request.
 */
public class AltchaService {

    private final AltchaVerifier verifier;

    /**
     * Creates the service.
     *
     * @param verifier the verifier that checks the payloads
     */
    public AltchaService(final AltchaVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
    }

    /**
     * Verifies the payload of the widget (form field {@code altcha}) and marks the challenge as used.
     *
     * @param payload the Base64 payload, or {@code null}
     * @throws AltchaVerificationException if the payload is missing, invalid, expired or was used
     *                                     before; the message is in the language of the current request
     */
    public void requireVerified(final @Nullable String payload) {
        final AltchaVerifier.VerificationResult result = verifier.verify(payload);
        if (!result.verified()) {
            throw new AltchaVerificationException(result, LocaleContextHolder.getLocale());
        }
    }

    /**
     * Creates a new signed challenge.
     *
     * @return the challenge as JSON for the widget
     */
    public String createChallenge() {
        return verifier.createChallenge();
    }
}
