package com.forgeci.dto;

import com.forgeci.model.JobStatus;
import java.util.UUID;

public record JobResult(
        UUID jobId,
        JobStatus status,
        Integer exitCode,
        String errorMessage) {
}