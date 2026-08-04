package com.forgeci.dto;

import com.forgeci.model.JobStatus;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        JobStatus status) {
}