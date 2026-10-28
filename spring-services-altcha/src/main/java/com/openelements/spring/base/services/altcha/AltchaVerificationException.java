package com.openelements.spring.base.services.altcha;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import org.jspecify.annotations.Nullable;

/**
 * Thrown by {@link AltchaService#requireVerified(String)} if a request does not carry a valid ALTCHA
 * solution. The module answers it with status 400 and {@code {"code": …, "message": …}}
 * ({@link AltchaExceptionHandler}).
 */
public class AltchaVerificationException extends RuntimeException {

    private static final String BUNDLE = "com/openelements/spring/base/services/altcha/messages";

    /**
     * The failed verification result.
     */
    private final AltchaVerifier.VerificationResult result;

    /**
     * Creates the exception with a message for the visitor.
     *
     * @param result the failed verification result
     * @param locale the language of the message; {@code null} means English
     * @throws IllegalArgumentException if {@code result} is {@link AltchaVerifier.VerificationResult#VERIFIED}
     */
    public AltchaVerificationException(final AltchaVerifier.VerificationResult result,
                                       final @Nullable Locale locale) {
        super(message(result, locale));
        this.result = result;
        if (result.verified()) {
            throw new IllegalArgumentException("A verified result is no failure");
        }
    }

    /**
     * Returns the failed verification result.
     *
     * @return the result
     */
    public AltchaVerifier.VerificationResult result() {
        return result;
    }

    /**
     * Returns a stable code for clients, which does not change with the language of the message.
     *
     * @return the code, e.g. {@code altcha-missing}
     */
    public String code() {
        return "altcha-" + result.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the message for the visitor from {@code messages*.properties} next to this class (English,
     * German). Unknown languages get English, independent of the default locale of the JVM.
     */
    static String message(final AltchaVerifier.VerificationResult result, final @Nullable Locale locale) {
        Objects.requireNonNull(result, "result must not be null");
        final String key = result.name().toLowerCase(Locale.ROOT);
        try {
            final ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale == null ? Locale.ENGLISH : locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            return bundle.getString(key);
        } catch (final MissingResourceException e) {
            return key;
        }
    }
}
