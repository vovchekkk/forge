package com.forgeci.server.web.dto;

import com.forgeci.model.PipelineRunStatus;
import java.time.Instant;
import java.util.UUID;

public record PipelineRunResponse(
        UUID id,
        UUID pipelineId,
        PipelineRunStatus status,
        String revision,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {
}