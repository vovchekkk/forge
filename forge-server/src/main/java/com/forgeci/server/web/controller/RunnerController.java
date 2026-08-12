package com.forgeci.server.web.controller;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.dto.LogChunkRequest;
import com.forgeci.dto.RunnerHeartbeatRequest;
import com.forgeci.dto.RunnerRegistrationRequest;
import com.forgeci.dto.RunnerRegistrationResponse;
import com.forgeci.model.RunnerStatus;
import com.forgeci.server.application.ConflictException;
import com.forgeci.server.application.InvalidRunnerTokenException;
import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.RunnerEntity;
import com.forgeci.server.repository.RunnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PostMapping("/register")
    public RunnerRegistrationResponse register(@RequestBody RunnerRegistrationRequest request) {
        RunnerEntity runner = runnerService.register(request.name(), request.token());
        return new RunnerRegistrationResponse(runner.getId(), runner.getName(), runner.getStatus());
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id,
                                          @RequestHeader(value = "X-Forge-Token", required = false) String token,
                                          @RequestBody(required = false) RunnerHeartbeatRequest request) {
        authorize(id, token);
        RunnerStatus status = request == null ? null : request.status();
        UUID currentJobId = request == null ? null : request.currentJobId();
        runnerService.heartbeat(id, status, currentJobId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/jobs/next")
    public ResponseEntity<JobClaim> nextJob(@PathVariable UUID id,
                                            @RequestHeader(value = "X-Forge-Token", required = false) String token) {
        authorize(id, token);
        Optional<JobClaim> claim = runnerService.claimNextJob(id);
        return claim.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/{id}/jobs/{jobId}/logs")
    public ResponseEntity<Void> logs(@PathVariable UUID id,
                                     @PathVariable UUID jobId,
                                     @RequestHeader(value = "X-Forge-Token", required = false) String token,
                                     @RequestBody LogChunkRequest request) {
        authorize(id, token);
        runnerService.appendLogs(id, jobId, request.lines());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/jobs/{jobId}/result")
    public ResponseEntity<Void> result(@PathVariable UUID id,
                                       @PathVariable UUID jobId,
                                       @RequestHeader(value = "X-Forge-Token", required = false) String token,
                                       @RequestBody JobResult result) {
        authorize(id, token);
        if (!jobId.equals(result.jobId())) {
            throw new ConflictException("Job id in path and body do not match");
        }
        runnerService.reportResult(id, result);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public RunnerEntity status(@PathVariable UUID id,
                               @RequestHeader(value = "X-Forge-Token", required = false) String token) {
        authorize(id, token);
        return runnerService.getRunner(id);
    }

    private void authorize(UUID id, String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidRunnerTokenException("Missing runner token");
        }
        RunnerEntity runner = runnerRepository.findById(id)
                .orElseThrow(() -> new InvalidRunnerTokenException("Runner not found: " + id));
        if (!runner.getToken().equals(token)) {
            throw new InvalidRunnerTokenException("Invalid runner token");
        }
    }
}