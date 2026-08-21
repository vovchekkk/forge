package com.forgeci.runner.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetrySupportTest {

    @Test
    void succeedsOnFirstAttempt() {
        AtomicInteger calls = new AtomicInteger();
        String result = RetrySupport.withRetries(3, 1, 10, "op", () -> {
            calls.incrementAndGet();
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = RetrySupport.withRetries(5, 1, 10, "op", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxAttemptsAndThrowsLastError() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> RetrySupport.withRetries(3, 1, 10, "op", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("always-fails");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("always-fails");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void voidVariantRetries() {
        AtomicInteger calls = new AtomicInteger();
        RetrySupport.withRetriesVoid(5, 1, 10, "op", () -> {
            if (calls.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
        });
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void backoffStaysWithinBounds() {
        assertThat(RetrySupport.backoff(1, 500, 5000)).isBetween(500L, 5000L);
        assertThat(RetrySupport.backoff(10, 500, 5000)).isLessThanOrEqualTo(5000L);
    }
}
