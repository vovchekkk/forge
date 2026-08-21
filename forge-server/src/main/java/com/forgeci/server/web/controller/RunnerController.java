package com.forgeci.server.web.controller;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.dto.JobStatusResponse;
import com.forgeci.dto.LogChunkRequest;
import com.forgeci.dto.RunnerHeartbeatRequest;
import com.forgeci.dto.RunnerRegistrationRequest;
import com.forgeci.dto.RunnerRegistrationResponse;
import com.forgeci.model.RunnerStatus;
import com.forgeci.server.application.ConflictException;
import com.forgeci.server.application.NotFoundException;
import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.RunnerEntity;
import com.forgeci.server.repository.RunnerRepository;
import com.forgeci.server.security.SecurityUtils;
import com.forgeci.server.web.dto.RunnerCreateResponse;
import com.forgeci.server.web.dto.RunnerResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runners")
public class RunnerController {

    private final RunnerService runnerService;
    private final RunnerRepository runnerRepository;

    public RunnerController(RunnerService runnerService, RunnerRepository runnerRepository) {
        this.runnerService = runnerService;
        this.runnerRepository = runnerRepository;
    }

    /**
     * Issue a new runner credential (user principal). The registration token is returned
     * exactly once — it is never stored in plaintext server-side.
     */
    @PostMapping
    public RunnerCreateResponse create(@RequestBody RunnerRegistrationRequest request) {
        UUID ownerId = SecurityUtils.requireUserId();
        RunnerService.RegistrationIssue issue = runnerService.createCredential(ownerId, request.name());
        RunnerEntity runner = issue.runner();
        return new RunnerCreateResponse(runner.getId(), runner.getName(), runner.getStatus(),
                issue.registrationToken());
    }

    /** List the authenticated user's runners. */
    @GetMapping
    public List<RunnerResponse> list() {
        UUID ownerId = SecurityUtils.requireUserId();
        return runnerRepository.findByOwner_Id(ownerId).stream().map(this::toResponse).toList();
    }

    /** Register a runner instance with its credential (public; idempotent). */
    @PostMapping("/register")
    public RunnerRegistrationResponse register(@RequestBody RunnerRegistrationRequest request) {
        RunnerEntity runner = runnerService.register(request.name(), request.token());
        return new RunnerRegistrationResponse(runner.getId(), runner.getName(), runner.getStatus());
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id,
                                          @RequestBody(required = false) RunnerHeartbeatRequest request) {
        runnerService.heartbeat(id, request == null ? null : request.status(),
                request == null ? null : request.currentJobId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/jobs/next")
    public ResponseEntity<JobClaim> nextJob(@PathVariable UUID id) {
        Optional<JobClaim> claim = runnerService.claimNextJob(id);
        return claim.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/{id}/jobs/{jobId}/logs")
    public ResponseEntity<Void> logs(@PathVariable UUID id,
                                     @PathVariable UUID jobId,
                                     @RequestBody LogChunkRequest request) {
        runnerService.appendLogs(id, jobId, request.lines());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/jobs/{jobId}/result")
    public ResponseEntity<Void> result(@PathVariable UUID id,
                                       @PathVariable UUID jobId,
                                       @RequestBody JobResult result) {
        if (!jobId.equals(result.jobId())) {
            throw new ConflictException("Job id in path and body do not match");
        }
        runnerService.reportResult(id, result);
        return ResponseEntity.ok().build();
    }

    /** Runner-scoped job status. */
    @GetMapping("/{id}/jobs/{jobId}/status")
    public JobStatusResponse jobStatus(@PathVariable UUID id, @PathVariable UUID jobId) {
        JobEntity job = runnerService.getRunnerJob(id, jobId);
        return new JobStatusResponse(job.getId(), job.getStatus());
    }

    /** Runner status: a runner reads its own status; a user reads a runner they own. */
    @GetMapping("/{id}")
    public RunnerResponse status(@PathVariable UUID id) {
        RunnerEntity runner = runnerService.getRunner(id);
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID userId) {
            if (runner.getOwnerId() == null || !runner.getOwnerId().equals(userId)) {
                throw new NotFoundException("Runner not found: " + id);
            }
        }
        return toResponse(runner);
    }

    /** Revoke a runner credential (user principal, ownership enforced). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireUserId();
        RunnerEntity runner = runnerService.getRunner(id);
        if (runner.getOwnerId() == null || !runner.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Runner not found: " + id);
        }
        runnerService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    private RunnerResponse toResponse(RunnerEntity entity) {
        return new RunnerResponse(entity.getId(), entity.getName(), entity.getStatus(),
                entity.getLastHeartbeatAt());
    }
}