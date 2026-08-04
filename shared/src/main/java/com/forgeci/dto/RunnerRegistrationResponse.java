package com.forgeci.dto;

import com.forgeci.model.RunnerStatus;
import java.util.UUID;

public record RunnerRegistrationResponse(
        UUID runnerId,
        String name,
        RunnerStatus status) {
}