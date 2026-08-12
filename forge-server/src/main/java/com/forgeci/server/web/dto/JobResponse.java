package com.forgeci.server.web.dto;

import com.forgeci.model.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String name,
        JobStatus status,
        String image,
        Integer timeout,
        Instant startedAt,
        Instant finishedAt,
        Integer exitCode,
        String errorMessage) {
}