package com.forgeci.server.web.dto;

import com.forgeci.model.RunnerStatus;
import java.time.Instant;
import java.util.UUID;

public record RunnerResponse(
        UUID id,
        String name,
        RunnerStatus status,
        Instant lastHeartbeatAt) {
}