package dev.rafaeltoledo.ledger.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with "equal jitter": attempt {@code n} waits a random time between half and
 * all of {@code base * 2^(n-1)}, capped at {@link #MAX_BACKOFF}. Jitter spreads out clients that
 * collided on the same row so they do not collide again in lockstep.
 */
public record RetryPolicy(int maxAttempts, Duration baseBackoff, Sleeper sleeper) {

    static final Duration MAX_BACKOFF = Duration.ofSeconds(1);

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Objects.requireNonNull(baseBackoff, "baseBackoff");
        Objects.requireNonNull(sleeper, "sleeper");
    }

    public Duration backoff(int attempt) {
        long exponential = baseBackoff.toNanos() << Math.min(attempt - 1, 20);
        long capped = Math.min(exponential, MAX_BACKOFF.toNanos());
        long half = capped / 2;
        return Duration.ofNanos(half + ThreadLocalRandom.current().nextLong(capped - half + 1));
    }

    @FunctionalInterface
    public interface Sleeper {

        Sleeper THREAD_SLEEP = duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while backing off", e);
            }
        };

        void sleep(Duration duration);
    }
}
