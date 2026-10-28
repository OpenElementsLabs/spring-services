package com.openelements.spring.base.services.altcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class AltchaServiceTest {

    private static final String KEY = "unit-test-altcha-key-with-at-least-32-characters";

    private final AltchaService service = new AltchaService(
            new AltchaVerifier(AltchaVerifierTest.properties(KEY), new InMemoryReplayStore()));

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void acceptsASolvedChallengeOnce() throws Exception {
        final String payload = AltchaSolver.solve(service.createChallenge());

        assertThatCode(() -> service.requireVerified(payload)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireVerified(payload))
                .isInstanceOfSatisfying(AltchaVerificationException.class, e -> {
                    assertThat(e.result()).isEqualTo(AltchaVerifier.VerificationResult.REUSED);
                    assertThat(e.code()).isEqualTo("altcha-reused");
                });
    }

    @Test
    void rejectsMissingAndInvalidPayloadsWithStableCodes() {
        assertThatThrownBy(() -> service.requireVerified(null))
                .isInstanceOfSatisfying(AltchaVerificationException.class,
                        e -> assertThat(e.code()).isEqualTo("altcha-missing"));
        assertThatThrownBy(() -> service.requireVerified("bm8gYWx0Y2hh"))
                .isInstanceOfSatisfying(AltchaVerificationException.class,
                        e -> assertThat(e.code()).isEqualTo("altcha-invalid"));
    }

    @Test
    void messagesFollowTheLanguageOfTheRequest() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThatThrownBy(() -> service.requireVerified(null))
                .hasMessageStartingWith("Die Prüfung gegen automatisierte Anfragen fehlt.");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThatThrownBy(() -> service.requireVerified(null))
                .hasMessageStartingWith("The check against automated requests is missing.");
    }

    @Test
    void unknownLanguagesGetEnglishIndependentOfTheJvmDefault() {
        final Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(AltchaVerificationException.message(AltchaVerifier.VerificationResult.EXPIRED,
                    Locale.forLanguageTag("fr"))).startsWith("The check against automated requests has expired.");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void everyFailureHasAMessageInBothLanguages() {
        for (AltchaVerifier.VerificationResult result : AltchaVerifier.VerificationResult.values()) {
            if (result.verified()) {
                continue;
            }
            for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.GERMAN}) {
                final String message = AltchaVerificationException.message(result, locale);
                assertThat(message).as(result + " " + locale).isNotEqualTo(result.name().toLowerCase(Locale.ROOT))
                        .endsWith(".");
            }
        }
    }

    @Test
    void aVerifiedResultIsNoFailure() {
        assertThatThrownBy(() -> new AltchaVerificationException(AltchaVerifier.VerificationResult.VERIFIED,
                Locale.ENGLISH)).isInstanceOf(IllegalArgumentException.class);
    }
}
