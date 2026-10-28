package com.openelements.spring.base.services.altcha;

import java.time.Instant;

/**
 * Remembers used challenges, so that each solution is accepted once.
 *
 * <p>The default is {@link InMemoryReplayStore}, which is correct for a single instance only. An
 * application that runs several instances registers its own bean (database, Redis, …) and the
 * library backs off.
 */
public interface ReplayStore {

    /**
     * Marks a challenge as used.
     *
     * @param signature the signature of the challenge
     * @param expiresAt until when the challenge must be remembered; after it, the challenge is rejected
     *                  as expired anyway
     * @return {@code true} if the challenge was not used before
     */
    boolean markUsed(String signature, Instant expiresAt);
}
