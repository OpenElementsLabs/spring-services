/**
 * Bot protection for endpoints that accept requests without authentication, with
 * <a href="https://altcha.org">ALTCHA</a>: a self-hosted proof of work, without an external service
 * and without cookies.
 *
 * <p>The widget on the page fetches a signed challenge from
 * {@link com.openelements.spring.base.services.altcha.AltchaChallengeController}, solves it in the
 * browser and sends the solution with the form. The endpoint that receives the form calls
 * {@link com.openelements.spring.base.services.altcha.AltchaService#requireVerified(String)}; a failed
 * check becomes a {@code 400} with a stable code and a message in the language of the request.
 *
 * <p>Null-marked package (JSpecify): every type is non-null unless annotated
 * {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package com.openelements.spring.base.services.altcha;

import org.jspecify.annotations.NullMarked;
