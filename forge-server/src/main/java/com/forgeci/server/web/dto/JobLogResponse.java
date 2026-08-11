package com.forgeci.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record JobLogResponse(
        UUID id,
        UUID jobId,
        String content,
        Instant createdAt) {
}