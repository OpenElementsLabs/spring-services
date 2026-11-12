package com.openelements.spring.base.services.altcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.problem.ProblemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link AltchaProblemType}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two things:
 *
 * <ul>
 *   <li><b>The slugs equal the {@code code} this module published before problem details existed.</b>
 *       {@link #slugsMatchThePreviousCodes} derives the expected slug from
 *       {@link AltchaVerificationException#code()} for every failure result, so the migration promise —
 *       "a client that keyed on {@code code} compares the {@code type} URI instead, with the same word
 *       at the end" — is enforced by the build rather than by the upgrade guide alone.
 *   <li><b>Every constant satisfies the {@link ProblemType} contract</b>, which the enum bypasses by
 *       implementing the interface directly instead of going through {@code ProblemType.of}.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context, no mocks.
 */
@DisplayName("AltchaProblemType")
class AltchaProblemTypeTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "MISSING, altcha-missing, Bot check missing",
            "INVALID, altcha-invalid, Bot check invalid",
            "EXPIRED, altcha-expired, Bot check expired",
            "REUSED, altcha-reused, Bot check reused",
    })
    @DisplayName("Slug, status and title of each constant are frozen")
    void constantsAreFrozen(final AltchaProblemType type, final String slug, final String title) {
        assertThat(type.slug()).isEqualTo(slug);
        assertThat(type.title()).isEqualTo(title);
        assertThat(type.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(value = AltchaVerifier.VerificationResult.class, names = "VERIFIED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("The slug equals the code the module published before problem details")
    void slugsMatchThePreviousCodes(final AltchaVerifier.VerificationResult result) {
        final String previousCode = new AltchaVerificationException(result, null).code();

        assertThat(AltchaProblemType.forResult(result).slug()).isEqualTo(previousCode);
    }

    @ParameterizedTest
    @EnumSource(AltchaProblemType.class)
    @DisplayName("Every constant satisfies the ProblemType contract")
    void everyConstantIsValid(final AltchaProblemType type) {
        ProblemType.validate(type);
    }

    @Test
    @DisplayName("A verified result is not a problem")
    void verifiedResultIsRejected() {
        assertThatThrownBy(() -> AltchaProblemType.forResult(AltchaVerifier.VerificationResult.VERIFIED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A null result is rejected")
    void nullResultIsRejected() {
        assertThatThrownBy(() -> AltchaProblemType.forResult(null))
                .isInstanceOf(NullPointerException.class);
    }
}
