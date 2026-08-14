package com.forgeci.server.application;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.model.RunnerStatus;
import com.forgeci.server.config.ForgeProperties;
import com.forgeci.server.domain.StateMachine;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.JobLogEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.entity.RunnerEntity;
import com.forgeci.server.repository.JobLogRepository;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.repository.PipelineRunRepository;
import com.forgeci.server.repository.RunnerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunnerService {

    private static final Logger log = LoggerFactory.getLogger(RunnerService.class);

    private final RunnerRepository runnerRepository;
    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final PipelineRunRepository runRepository;
    private final SchedulerService schedulerService;
    private final ForgeProperties properties;

    public RunnerService(RunnerRepository runnerRepository, JobRepository jobRepository,
                         JobLogRepository jobLogRepository, PipelineRunRepository runRepository,
                         SchedulerService schedulerService, ForgeProperties properties) {
        this.runnerRepository = runnerRepository;
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.runRepository = runRepository;
        this.schedulerService = schedulerService;
        this.properties = properties;
    }

    @Transactional
    public RunnerEntity register(String name, String token) {
        if (name == null || name.isBlank()) {
            throw new InvalidRunnerTokenException("Runner name is required");
        }
        if (token == null || token.isBlank()) {
            throw new InvalidRunnerTokenException("Runner token is required");
        }
        RunnerEntity runner = new RunnerEntity(name, token, RunnerStatus.ONLINE);
        runner.setLastHeartbeatAt(Instant.now());
        return runnerRepository.save(runner);
    }

    @Transactional
    public RunnerEntity heartbeat(UUID runnerId, RunnerStatus reportedStatus, UUID currentJobId) {
        RunnerEntity runner = getRunner(runnerId);
        runner.setLastHeartbeatAt(Instant.now());
        runner.setStatus(resolveStatus(runner, reportedStatus, currentJobId));
        return runnerRepository.save(runner);
    }

    private RunnerStatus resolveStatus(RunnerEntity runner, RunnerStatus reported, UUID currentJobId) {
        if (currentJobId != null) {
            return RunnerStatus.BUSY;
        }
        if (reported != null) {
            return reported;
        }
        return runner.getStatus();
    }

    /**
     * Atomically claim the next READY job for a runner. Uses FOR UPDATE SKIP LOCKED
     * in PostgreSQL so concurrent runners never receive the same job.
     */
    @Transactional
    public Optional<JobClaim> claimNextJob(UUID runnerId) {
        RunnerEntity runner = getRunner(runnerId);
        runner.setLastHeartbeatAt(Instant.now());
        runner.setStatus(RunnerStatus.ONLINE);
        runnerRepository.save(runner);

        Optional<JobEntity> next = jobRepository.claimNextJob();
        if (next.isEmpty()) {
            return Optional.empty();
        }
        JobEntity job = next.get();
        StateMachine.ensureJobTransition(job.getStatus(), JobStatus.RUNNING);
        job.setStatus(JobStatus.RUNNING);
        job.setRunner(runner);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        PipelineRunEntity run = job.getPipelineRun();
        if (run.getStatus() == PipelineRunStatus.QUEUED) {
            StateMachine.ensureRunTransition(PipelineRunStatus.QUEUED, PipelineRunStatus.RUNNING);
            run.setStatus(PipelineRunStatus.RUNNING);
            run.setStartedAt(Instant.now());
            runRepository.save(run);
        }

        JobClaim claim = new JobClaim(
                job.getId(),
                job.getName(),
                job.getPipelineRun().getId(),
                run.getPipeline().getProject().getRepositoryUrl(),
                run.getRevision(),
                job.getImage(),
                JsonUtil.readStringList(job.getCommands()),
                JsonUtil.readStringMap(job.getEnvironment()),
                job.getTimeout());
        log.info("Claimed job {} for runner {}", job.getId(), runner.getId());
        return Optional.of(claim);
    }

    @Transactional
    public void appendLogs(UUID runnerId, UUID jobId, List<String> lines) {
        RunnerEntity runner = getRunner(runnerId);
        JobEntity job = getJob(jobId);
        if (!job.getRunner().getId().equals(runner.getId())) {
            throw new ConflictException("Job " + jobId + " is not assigned to runner " + runnerId);
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<JobLogEntity> logs = lines.stream()
                .map(line -> new JobLogEntity(job, line))
                .toList();
        jobLogRepository.saveAll(logs);
    }

    @Transactional
    public void reportResult(UUID runnerId, JobResult result) {
        RunnerEntity runner = getRunner(runnerId);
        JobEntity job = getJob(result.jobId());
        if (job.getRunner() == null || !job.getRunner().getId().equals(runner.getId())) {
            throw new ConflictException("Job " + result.jobId() + " is not assigned to runner " + runnerId);
        }
        StateMachine.ensureJobTransition(job.getStatus(), result.status());
        job.setStatus(result.status());
        job.setExitCode(result.exitCode());
        job.setErrorMessage(result.errorMessage());
        job.setFinishedAt(Instant.now());
        jobRepository.save(job);

        boolean done = result.status() == JobStatus.SUCCESS
                || result.status() == JobStatus.FAILED
                || result.status() == JobStatus.CANCELED;
        if (done) {
            runner.setStatus(RunnerStatus.ONLINE);
            runnerRepository.save(runner);
        }
        log.info("Job {} finished with status {} exit={}", job.getId(), result.status(), result.exitCode());
        schedulerService.updateRun(job.getPipelineRun().getId());
    }

    /** Detect stale runners and fail their in-flight jobs. */
    @Scheduled(fixedDelayString = "${forge.runner.offline-check-interval:5s}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void detectOfflineRunners() {
        Duration threshold = properties.getRunner().getOfflineThreshold();
        Instant cutoff = Instant.now().minus(threshold);
        List<RunnerEntity> stale = runnerRepository.findStaleRunners(cutoff);
        for (RunnerEntity runner : stale) {
            log.warn("Runner {} ({}) is offline (last heartbeat {})", runner.getId(), runner.getName(), runner.getLastHeartbeatAt());
            runner.setStatus(RunnerStatus.OFFLINE);
            runnerRepository.save(runner);

            List<JobEntity> inFlight = jobRepository.findByRunnerIdAndStatuses(
                    runner.getId(), List.of(JobStatus.RUNNING, JobStatus.QUEUED));
            for (JobEntity job : inFlight) {
                StateMachine.ensureJobTransition(job.getStatus(), JobStatus.FAILED);
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("Runner went offline");
                job.setFinishedAt(Instant.now());
                jobRepository.save(job);
                schedulerService.updateRun(job.getPipelineRun().getId());
            }
        }
    }

    @Transactional(readOnly = true)
    public RunnerEntity getRunner(UUID runnerId) {
        return runnerRepository.findById(runnerId)
                .orElseThrow(() -> new NotFoundException("Runner not found: " + runnerId));
    }

    @Transactional(readOnly = true)
    public JobEntity getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
    }
}