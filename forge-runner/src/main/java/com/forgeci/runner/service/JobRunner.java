package com.forgeci.runner.service;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.model.JobStatus;
import com.forgeci.model.RunnerStatus;
import com.forgeci.runner.client.ServerApiClient;
import com.forgeci.runner.docker.DockerExecutor;
import com.forgeci.runner.git.GitCheckout;
import com.forgeci.runner.workspace.WorkspaceManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class JobRunner implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final ServerApiClient apiClient;
    private final GitCheckout gitCheckout;
    private final DockerExecutor dockerExecutor;
    private final WorkspaceManager workspaceManager;
    private final AtomicReference<UUID> currentJob = new AtomicReference<>();
    private volatile boolean registered = false;

    public JobRunner(ServerApiClient apiClient, GitCheckout gitCheckout, DockerExecutor dockerExecutor,
                     WorkspaceManager workspaceManager) {
        this.apiClient = apiClient;
        this.gitCheckout = gitCheckout;
        this.dockerExecutor = dockerExecutor;
        this.workspaceManager = workspaceManager;
    }

    /** Periodic poll loop - the runner is not a scheduler, it only picks up work. */
    @Scheduled(fixedDelayString = "${forge.runner.poll-interval:5s}")
    public void poll() {
        if (!registered) {
            return;
        }
        if (currentJob.get() != null) {
            return;
        }
        Optional<JobClaim> next = apiClient.nextJob();
        next.ifPresent(this::execute);
    }

    @Scheduled(fixedDelayString = "${forge.runner.heartbeat-interval:10s}")
    public void heartbeat() {
        if (!registered) {
            return;
        }
        apiClient.heartbeat(currentJob.get() == null ? RunnerStatus.ONLINE : RunnerStatus.BUSY, currentJob.get());
    }

    private void execute(JobClaim job) {
        currentJob.set(job.jobId());
        apiClient.heartbeat(RunnerStatus.BUSY, job.jobId());
        log.info("Executing job {} ({})", job.jobId(), job.jobName());
        try {
            Path workspace = workspaceManager.create(job.jobId());
            gitCheckout.cloneAndCheckout(job.repositoryUrl(), job.revision(), workspace);

            Duration timeout = job.timeoutSeconds() == null
                    ? Duration.ofHours(1)
                    : Duration.ofSeconds(job.timeoutSeconds());

            DockerExecutor.CommandResult result = dockerExecutor.runJobCommands(
                    job.image(), job.commands(), job.environment(), workspace.toString(), timeout,
                    () -> apiClient.jobStatus(job.jobId()) == JobStatus.CANCELED,
                    line -> apiClient.appendLogs(job.jobId(), java.util.List.of(line)));

            JobStatus status = result.canceled() ? JobStatus.CANCELED
                    : result.timedOut() ? JobStatus.FAILED
                    : result.exitCode() == 0 ? JobStatus.SUCCESS : JobStatus.FAILED;
            apiClient.reportResult(new JobResult(job.jobId(), status, result.exitCode(),
                    result.errorMessage()));
        } catch (Exception e) {
            log.error("Job {} failed", job.jobId(), e);
            apiClient.reportResult(new JobResult(job.jobId(), JobStatus.FAILED, -1, e.getMessage()));
        } finally {
            workspaceManager.cleanup(job.jobId());
            currentJob.set(null);
            apiClient.heartbeat(RunnerStatus.ONLINE, null);
        }
    }

    public void register() {
        if (!registered) {
            apiClient.register(name());
            registered = true;
            log.info("Runner registered, starting poll loop");
        }
    }

    private String name() {
        return System.getenv("FORGE_RUNNER_NAME") != null
                ? System.getenv("FORGE_RUNNER_NAME")
                : "runner-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public void destroy() {
        UUID job = currentJob.get();
        if (job != null) {
            log.warn("Shutting down with active job {}", job);
        }
    }
}