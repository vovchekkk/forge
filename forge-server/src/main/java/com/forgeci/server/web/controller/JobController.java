package com.forgeci.server.web.controller;

import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.JobLogEntity;
import com.forgeci.server.repository.JobLogRepository;
import com.forgeci.server.repository.JobRepository;
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

    public JobController(JobRepository jobRepository, JobLogRepository jobLogRepository, RunnerService runnerService) {
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.runnerService = runnerService;
    }

    @GetMapping("/pipeline-runs/{runId}/jobs")
    public List<JobResponse> listByRun(@PathVariable UUID runId) {
        return jobRepository.findByPipelineRunId(runId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/jobs/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return toResponse(runnerService.getJob(id));
    }

    @GetMapping("/jobs/{id}/status")
    public com.forgeci.dto.JobStatusResponse status(@PathVariable UUID id) {
        JobEntity job = runnerService.getJob(id);
        return new com.forgeci.dto.JobStatusResponse(job.getId(), job.getStatus());
    }

    @GetMapping("/jobs/{id}/logs")
    public List<JobLogResponse> logs(@PathVariable UUID id) {
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