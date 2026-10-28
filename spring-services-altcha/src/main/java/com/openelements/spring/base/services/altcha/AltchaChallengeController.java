package com.openelements.spring.base.services.altcha;

import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands out challenges for the widget at {@code openelements.altcha.challenge-path}.
 *
 * <p>The endpoint must be reachable without authentication, since the visitor of a public form has
 * none. The module therefore registers its own security filter chain for exactly this path (see
 * {@code AltchaAutoConfiguration}).
 */
@RestController
public class AltchaChallengeController {

    private final AltchaService service;

    /**
     * Creates the controller.
     *
     * @param service the service that creates the challenges
     */
    public AltchaChallengeController(final AltchaService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * Creates a challenge. It is never cached, since each one can be used once.
     *
     * @return the challenge as JSON
     */
    @GetMapping(value = "${openelements.altcha.challenge-path:" + AltchaProperties.DEFAULT_CHALLENGE_PATH + "}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> challenge() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.createChallenge());
    }
}
