package com.openelements.spring.base.services.altcha;

import com.openelements.spring.base.problem.ProblemType;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * The problem types of a failed bot check.
 *
 * <p>They live in this module rather than in {@code spring-services-core}'s
 * {@link com.openelements.spring.base.problem.StandardProblemType}, so core knows no slug of an optional
 * module. The slugs are the ones the module published as {@code code} before RFC 9457 problems existed, so a
 * client that branched on {@code "altcha-expired"} now finds the same word at the end of the {@code type}
 * URI.
 *
 * <p>All four are {@code 400}: a failed proof of work is a defect of the request, not of the visitor's
 * authorisation. The title describes the type in English; the occurrence-specific text goes into
 * {@code detail} and <em>is</em> localised, from the request's {@code Accept-Language}.
 */
public enum AltchaProblemType implements ProblemType {

    /** No payload: the widget did not run, or the form field is missing. */
    MISSING("altcha-missing", "Bot check missing"),

    /** Not parseable, wrong signature, or wrong solution. */
    INVALID("altcha-invalid", "Bot check invalid"),

    /** The challenge has expired. */
    EXPIRED("altcha-expired", "Bot check expired"),

    /** The challenge was already used. */
    REUSED("altcha-reused", "Bot check reused");

    private final String slug;
    private final String title;

    AltchaProblemType(final String slug, final String title) {
        this.slug = slug;
        this.title = title;
    }

    @Override
    public String slug() {
        return slug;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String title() {
        return title;
    }

    /**
     * Returns the problem type for a failed verification.
     *
     * @param result the failed result
     * @return the matching problem type
     * @throws NullPointerException     if {@code result} is {@code null}
     * @throws IllegalArgumentException if {@code result} is
     *                                  {@link AltchaVerifier.VerificationResult#VERIFIED} — a success is
     *                                  not a problem
     */
    public static AltchaProblemType forResult(final AltchaVerifier.VerificationResult result) {
        Objects.requireNonNull(result, "result must not be null");
        if (result.verified()) {
            throw new IllegalArgumentException("A verified result is no problem");
        }
        return valueOf(result.name());
    }
}
