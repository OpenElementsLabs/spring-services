package com.openelements.spring.base.services.altcha;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps used challenges on the heap until they expire. Correct for a single instance only: a second
 * instance would accept a solution the first one has already seen.
 */
public final class InMemoryReplayStore implements ReplayStore {

    private final Map<String, Instant> used = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Creates a store on the system clock. */
    public InMemoryReplayStore() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a store on the given clock.
     *
     * @param clock the clock that decides which entries have expired
     */
    public InMemoryReplayStore(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public boolean markUsed(final String signature, final Instant expiresAt) {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        final Instant now = clock.instant();
        used.values().removeIf(expiry -> expiry.isBefore(now));
        return used.putIfAbsent(signature, expiresAt) == null;
    }

    /**
     * Returns the number of remembered challenges.
     *
     * @return the number of remembered challenges
     */
    public int size() {
        return used.size();
    }
}
