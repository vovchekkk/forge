package com.forgeci.dto;

import com.forgeci.model.RunnerStatus;
import java.util.UUID;

public record RunnerHeartbeatRequest(
        RunnerStatus status,
        UUID currentJobId) {
}