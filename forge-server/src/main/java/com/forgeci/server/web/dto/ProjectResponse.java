package com.forgeci.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String repositoryUrl,
        String repositoryBranch,
        Instant createdAt) {
}