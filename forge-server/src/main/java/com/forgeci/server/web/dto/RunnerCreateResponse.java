package com.forgeci.server.web.dto;

import com.forgeci.model.RunnerStatus;
import java.util.UUID;

public record RunnerCreateResponse(UUID runnerId, String name, RunnerStatus status, String registrationToken) {
}