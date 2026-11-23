package dev.rafaeltoledo.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void backoff_doubles_each_attempt_with_jitter_in_the_upper_half() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(8), d -> { });

        for (int i = 0; i < 100; i++) {
            assertThat(policy.backoff(1)).isBetween(Duration.ofMillis(4), Duration.ofMillis(8));
            assertThat(policy.backoff(3)).isBetween(Duration.ofMillis(16), Duration.ofMillis(32));
        }
    }

    @Test
    void backoff_is_capped_to_avoid_overflow_on_large_attempt_numbers() {
        RetryPolicy policy = new RetryPolicy(100, Duration.ofMillis(8), d -> { });

        assertThat(policy.backoff(60)).isLessThanOrEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void requires_at_least_one_attempt() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofMillis(1), d -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_default_sleeper_really_waits() throws Exception {
        long start = System.nanoTime();
        RetryPolicy.Sleeper.THREAD_SLEEP.sleep(Duration.ofMillis(5));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(5));
    }
}
