package com.forgeci.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PipelineResponse(
        UUID id,
        UUID projectId,
        String name,
        String config,
        Instant createdAt) {
}