package com.forgeci.runner.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class RetrySupport {

    private static final Logger log = LoggerFactory.getLogger(RetrySupport.class);

    private RetrySupport() {}

    
    public static <T> T withRetries(int attempts, long initialDelayMillis, long maxDelayMillis,
                                    String what, Supplier<T> operation) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt == attempts) {
                    break;
                }
                long delay = backoff(attempt, initialDelayMillis, maxDelayMillis);
                log.warn("{} failed on attempt {}/{} ({}), retrying in {}ms",
                        what, attempt, attempts, e.getMessage(), delay);
                sleep(delay);
            }
        }
        throw last;
    }

    
    public static void withRetriesVoid(int attempts, long initialDelayMillis, long maxDelayMillis,
                                       String what, Runnable operation) {
        withRetries(attempts, initialDelayMillis, maxDelayMillis, what, () -> {
            operation.run();
            return null;
        });
    }

    
    static long backoff(int attempt, long initialDelayMillis, long maxDelayMillis) {
        long base = Math.min(maxDelayMillis, initialDelayMillis * (1L << (attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, base / 4));
        return Math.min(maxDelayMillis, base + jitter);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry sleep interrupted", e);
        }
    }
}
