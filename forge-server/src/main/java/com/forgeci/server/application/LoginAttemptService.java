package com.forgeci.server.application;

import com.forgeci.server.config.ForgeProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory login throttling. Tracks failed attempts per identity within a sliding
 * window and rejects further attempts once the limit is reached.
 *
 * <p>Limitation: state is per JVM instance and lost on restart — suitable for a single
 * server. A multi-instance deployment must replace this with a DB-backed implementation.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(ForgeProperties properties) {
        this.maxAttempts = properties.getSecurity().getLogin().getMaxAttempts();
        this.window = properties.getSecurity().getLogin().getLockoutWindow();
    }

    /** Returns true when the identity is currently throttled (too many recent failures). */
    public boolean isBlocked(String identity) {
        AttemptState state = attempts.get(identity);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            state.prune(window);
            return state.failures >= maxAttempts;
        }
    }

    /** Record a failed attempt for the identity. */
    public void recordFailure(String identity) {
        AttemptState state = attempts.computeIfAbsent(identity, k -> new AttemptState());
        synchronized (state) {
            state.prune(window);
            state.failures++;
            state.lastAttempt = Instant.now();
        }
    }

    /** Clear recorded failures after a successful authentication. */
    public void reset(String identity) {
        attempts.remove(identity);
    }

    private static final class AttemptState {
        int failures;
        Instant lastAttempt = Instant.now();

        void prune(Duration window) {
            if (lastAttempt != null && lastAttempt.plus(window).isBefore(Instant.now())) {
                failures = 0;
            }
        }
    }
}