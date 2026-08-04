package com.forgeci.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobClaim(
        UUID jobId,
        String jobName,
        UUID pipelineRunId,
        String repositoryUrl,
        String revision,
        String image,
        List<String> commands,
        Map<String, String> environment,
        Integer timeoutSeconds) {
}