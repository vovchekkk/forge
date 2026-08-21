package com.forgeci.server.application;

import com.forgeci.model.JobDefinition;
import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineDefinition;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.server.domain.Dag;
import com.forgeci.server.domain.StateMachine;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.repository.PipelineRepository;
import com.forgeci.server.repository.PipelineRunRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineRunService {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunService.class);

    private final PipelineRepository pipelineRepository;
    private final PipelineRunRepository runRepository;
    private final JobRepository jobRepository;
    private final PipelineService pipelineService;

    public PipelineRunService(PipelineRepository pipelineRepository, PipelineRunRepository runRepository,
                              JobRepository jobRepository, PipelineService pipelineService) {
        this.pipelineRepository = pipelineRepository;
        this.runRepository = runRepository;
        this.jobRepository = jobRepository;
        this.pipelineService = pipelineService;
    }

    @Transactional
    public PipelineRunEntity start(UUID ownerId, UUID pipelineId, String revision) {
        PipelineEntity pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new NotFoundException("Pipeline not found: " + pipelineId));
        UUID projectOwnerId = pipeline.getProject().getOwnerId();
        if (projectOwnerId == null || !projectOwnerId.equals(ownerId)) {
            throw new NotFoundException("Pipeline not found: " + pipelineId);
        }

        String snapshot = pipeline.getConfig();
        PipelineDefinition definition = pipelineService.parseAndValidate(snapshot);

        Map<String, JobDefinition> jobs = definition.getJobs();
        Map<String, List<String>> needs = new LinkedHashMap<>();
        for (Map.Entry<String, JobDefinition> e : jobs.entrySet()) {
            needs.put(e.getKey(), e.getValue().getNeeds() == null ? List.of() : e.getValue().getNeeds());
        }
        Dag dag = Dag.of(needs);
        if (dag.hasCycle()) {
            throw new InvalidPipelineException(List.of("Pipeline contains a circular dependency"));
        }

        PipelineRunEntity run = new PipelineRunEntity(pipeline, PipelineRunStatus.CREATED, snapshot, revision);
        run = runRepository.save(run);

        String defaultImage = definition.resolvedImage();
        for (Map.Entry<String, JobDefinition> e : jobs.entrySet()) {
            JobDefinition def = e.getValue();
            boolean isRoot = dag.needsOf(e.getKey()).isEmpty();
            JobEntity job = new JobEntity(
                    run,
                    e.getKey(),
                    isRoot ? JobStatus.READY : JobStatus.PENDING,
                    JsonUtil.write(def.getCommands()),
                    JsonUtil.write(def.getNeeds()),
                    def.getTimeout(),
                    JsonUtil.write(def.getEnvironment()),
                    defaultImage);
            jobRepository.save(job);
        }

        StateMachine.ensureRunTransition(PipelineRunStatus.CREATED, PipelineRunStatus.QUEUED);
        run.setStatus(PipelineRunStatus.QUEUED);
        return runRepository.save(run);
    }

    @Transactional
    public PipelineRunEntity get(UUID ownerId, UUID runId) {
        PipelineRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Pipeline run not found: " + runId));
        UUID projectOwnerId = run.getPipeline().getProject().getOwnerId();
        if (projectOwnerId == null || !projectOwnerId.equals(ownerId)) {
            throw new NotFoundException("Pipeline run not found: " + runId);
        }
        return run;
    }

    @Transactional
    public PipelineRunEntity cancel(UUID ownerId, UUID runId) {
        PipelineRunEntity run = get(ownerId, runId);
        if (run.getStatus() == PipelineRunStatus.SUCCESS
                || run.getStatus() == PipelineRunStatus.FAILED
                || run.getStatus() == PipelineRunStatus.CANCELED) {
            throw new ConflictException("Run " + runId + " already finished with status " + run.getStatus());
        }
        StateMachine.ensureRunTransition(run.getStatus(), PipelineRunStatus.CANCELED);
        run.setStatus(PipelineRunStatus.CANCELED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        List<JobEntity> jobs = jobRepository.findByPipelineRunId(runId);
        for (JobEntity job : jobs) {
            if (job.getStatus() == JobStatus.SUCCESS
                    || job.getStatus() == JobStatus.FAILED
                    || job.getStatus() == JobStatus.CANCELED
                    || job.getStatus() == JobStatus.SKIPPED) {
                continue;
            }
            if (job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.QUEUED) {
                job.setErrorMessage("Run was canceled");
            }
            StateMachine.ensureJobTransition(job.getStatus(), JobStatus.CANCELED);
            job.setStatus(JobStatus.CANCELED);
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
        log.info("Run {} canceled", runId);
        return run;
    }

    @Transactional(readOnly = true)
    public List<PipelineRunEntity> listByPipeline(UUID ownerId, UUID pipelineId) {
        PipelineEntity pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new NotFoundException("Pipeline not found: " + pipelineId));
        UUID projectOwnerId = pipeline.getProject().getOwnerId();
        if (projectOwnerId == null || !projectOwnerId.equals(ownerId)) {
            throw new NotFoundException("Pipeline not found: " + pipelineId);
        }
        return runRepository.findByPipelineId(pipelineId);
    }
}