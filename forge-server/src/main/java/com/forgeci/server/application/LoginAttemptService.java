package com.forgeci.server.application;

import com.forgeci.server.config.ForgeProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;


@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(ForgeProperties properties) {
        this.maxAttempts = properties.getSecurity().getLogin().getMaxAttempts();
        this.window = properties.getSecurity().getLogin().getLockoutWindow();
    }

    
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

    
    public void recordFailure(String identity) {
        AttemptState state = attempts.computeIfAbsent(identity, k -> new AttemptState());
        synchronized (state) {
            state.prune(window);
            state.failures++;
            state.lastAttempt = Instant.now();
        }
    }

    
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