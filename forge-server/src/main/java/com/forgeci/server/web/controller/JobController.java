package com.forgeci.server.web.controller;

import com.forgeci.dto.JobStatusResponse;
import com.forgeci.server.application.PipelineRunService;
import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.JobLogEntity;
import com.forgeci.server.repository.JobLogRepository;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.security.SecurityUtils;
import com.forgeci.server.web.dto.JobLogResponse;
import com.forgeci.server.web.dto.JobResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final RunnerService runnerService;
    private final PipelineRunService runService;

    public JobController(JobRepository jobRepository, JobLogRepository jobLogRepository,
                         RunnerService runnerService, PipelineRunService runService) {
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.runnerService = runnerService;
        this.runService = runService;
    }

    @GetMapping("/pipeline-runs/{runId}/jobs")
    public List<JobResponse> listByRun(@PathVariable UUID runId) {
        UUID ownerId = SecurityUtils.requireUserId();
        runService.get(ownerId, runId);
        return jobRepository.findByPipelineRunId(runId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/jobs/{id}")
    public JobResponse get(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireUserId();
        return toResponse(runnerService.getOwnedJob(ownerId, id));
    }

    @GetMapping("/jobs/{id}/status")
    public JobStatusResponse status(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireUserId();
        JobEntity job = runnerService.getOwnedJob(ownerId, id);
        return new JobStatusResponse(job.getId(), job.getStatus());
    }

    @GetMapping("/jobs/{id}/logs")
    public List<JobLogResponse> logs(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireUserId();
        runnerService.getOwnedJob(ownerId, id);
        return jobLogRepository.findByJobIdOrderByCreatedAtAsc(id).stream().map(this::toLogResponse).toList();
    }

    private JobResponse toResponse(JobEntity entity) {
        return new JobResponse(entity.getId(), entity.getName(), entity.getStatus(), entity.getImage(),
                entity.getTimeout(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getExitCode(), entity.getErrorMessage());
    }

    private JobLogResponse toLogResponse(JobLogEntity entity) {
        return new JobLogResponse(entity.getId(), entity.getJob().getId(), entity.getContent(), entity.getCreatedAt());
    }
}