package com.forgeci.server.application;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.server.domain.Dag;
import com.forgeci.server.domain.StateMachine;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.repository.PipelineRunRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final JobRepository jobRepository;
    private final PipelineRunRepository runRepository;

    public SchedulerService(JobRepository jobRepository, PipelineRunRepository runRepository) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
    }

    @Scheduled(fixedDelayString = "${forge.scheduler.interval:1s}")
    public void scanActiveRuns() {
        List<PipelineRunEntity> runs = runRepository.findByStatus(PipelineRunStatus.QUEUED);
        runs.addAll(runRepository.findByStatus(PipelineRunStatus.RUNNING));
        for (PipelineRunEntity run : runs) {
            try {
                updateRun(run.getId());
            } catch (Exception e) {
                log.error("Failed to update run {}", run.getId(), e);
            }
        }
    }

    /**
     * Update readiness of jobs in a run: PENDING -> READY once all deps SUCCESS,
     * PENDING -> SKIPPED when a dependency failed/canceled/skipped. Finalizes the
     * run when all jobs are terminal. Runs in a new transaction so concurrent
     * claims see consistent state.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRun(UUID runId) {
        PipelineRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Pipeline run not found: " + runId));
        if (run.getStatus() == PipelineRunStatus.SUCCESS
                || run.getStatus() == PipelineRunStatus.FAILED
                || run.getStatus() == PipelineRunStatus.CANCELED) {
            return;
        }

        List<JobEntity> jobs = jobRepository.findByPipelineRunId(runId);
        if (jobs.isEmpty()) {
            return;
        }

        Map<String, List<String>> needs = new LinkedHashMap<>();
        Map<String, JobEntity> byName = new LinkedHashMap<>();
        Map<String, JobStatus> statusByName = new LinkedHashMap<>();
        for (JobEntity job : jobs) {
            byName.put(job.getName(), job);
            needs.put(job.getName(), JsonUtil.readStringList(job.getNeeds()));
            statusByName.put(job.getName(), job.getStatus());
        }
        Dag dag = Dag.of(needs);

        boolean changed = false;
        for (JobEntity job : jobs) {
            if (job.getStatus() != JobStatus.PENDING) {
                continue;
            }
            Set<String> deps = dag.needsOf(job.getName());
            boolean allSucceeded = true;
            boolean anyBlocked = false;
            for (String dep : deps) {
                JobStatus depStatus = statusByName.get(dep);
                if (depStatus == JobStatus.SUCCESS) {
                    continue;
                }
                allSucceeded = false;
                if (depStatus == JobStatus.FAILED
                        || depStatus == JobStatus.CANCELED
                        || depStatus == JobStatus.SKIPPED) {
                    anyBlocked = true;
                }
            }
            if (anyBlocked) {
                StateMachine.ensureJobTransition(JobStatus.PENDING, JobStatus.SKIPPED);
                job.setStatus(JobStatus.SKIPPED);
                changed = true;
            } else if (allSucceeded) {
                StateMachine.ensureJobTransition(JobStatus.PENDING, JobStatus.READY);
                job.setStatus(JobStatus.READY);
                changed = true;
            }
        }
        if (changed) {
            jobRepository.saveAll(jobs);
            // Refresh status map used for finalization
            for (JobEntity job : jobs) {
                statusByName.put(job.getName(), job.getStatus());
            }
        }

        finalizeRun(run, statusByName);
    }

    /** Determine final run state when all jobs are terminal. */
    private void finalizeRun(PipelineRunEntity run, Map<String, JobStatus> statusByName) {
        long total = statusByName.size();
        long terminal = statusByName.values().stream()
                .filter(s -> s == JobStatus.SUCCESS
                        || s == JobStatus.FAILED
                        || s == JobStatus.CANCELED
                        || s == JobStatus.SKIPPED)
                .count();
        if (terminal < total) {
            return;
        }

        PipelineRunStatus finalStatus;
        if (statusByName.containsValue(JobStatus.FAILED)) {
            finalStatus = PipelineRunStatus.FAILED;
        } else if (statusByName.containsValue(JobStatus.CANCELED)) {
            finalStatus = PipelineRunStatus.CANCELED;
        } else {
            finalStatus = PipelineRunStatus.SUCCESS;
        }
        StateMachine.ensureRunTransition(run.getStatus(), finalStatus);
        run.setStatus(finalStatus);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        log.info("Run {} finalized with status {}", run.getId(), finalStatus);
    }
}