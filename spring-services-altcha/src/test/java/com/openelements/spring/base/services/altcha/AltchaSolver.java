package com.openelements.spring.base.services.altcha;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.altcha.altcha.v2.Altcha;

/**
 * Solves ALTCHA challenges like the widget does and encodes the payload of the form field {@code altcha}.
 */
public final class AltchaSolver {

    private AltchaSolver() {
    }

    /**
     * @param challengeJson the challenge as returned by {@code the challenge endpoint}
     * @return the Base64 payload for the form field {@code altcha}
     */
    public static String solve(final String challengeJson) throws Exception {
        // parsePayload is the only public JSON parser of the library: wrap the challenge with a dummy solution
        final Altcha.Challenge challenge = Altcha.parsePayload(base64("{\"challenge\":" + challengeJson
                + ",\"solution\":{\"counter\":0,\"derivedKey\":\"00\"}}")).challenge();
        return solve(challenge);
    }

    public static String solve(final Altcha.Challenge challenge) throws Exception {
        final Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf(challenge.parameters().algorithm()));
        return base64("{\"challenge\":" + challenge.toJson() + ",\"solution\":{\"counter\":" + solution.counter()
                + ",\"derivedKey\":\"" + solution.derivedKey() + "\"}}");
    }

    private static String base64(final String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
