package com.forgeci.server.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgeci.server.config.ForgeProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        ForgeProperties props = new ForgeProperties();
        props.getSecurity().getLogin().setMaxAttempts(3);
        props.getSecurity().getLogin().setLockoutWindow(Duration.ofMinutes(10));
        service = new LoginAttemptService(props);
    }

    @Test
    void notBlockedInitially() {
        assertThat(service.isBlocked("alice@test.com")).isFalse();
    }

    @Test
    void blockedAfterLimit() {
        service.recordFailure("alice@test.com");
        service.recordFailure("alice@test.com");
        assertThat(service.isBlocked("alice@test.com")).isFalse();
        service.recordFailure("alice@test.com");
        assertThat(service.isBlocked("alice@test.com")).isTrue();
    }

    @Test
    void resetClearsFailures() {
        service.recordFailure("alice@test.com");
        service.recordFailure("alice@test.com");
        service.recordFailure("alice@test.com");
        assertThat(service.isBlocked("alice@test.com")).isTrue();
        service.reset("alice@test.com");
        assertThat(service.isBlocked("alice@test.com")).isFalse();
    }

    @Test
    void throttlingIsPerIdentity() {
        service.recordFailure("alice@test.com");
        service.recordFailure("alice@test.com");
        service.recordFailure("alice@test.com");
        assertThat(service.isBlocked("alice@test.com")).isTrue();
        assertThat(service.isBlocked("bob@test.com")).isFalse();
    }
}
