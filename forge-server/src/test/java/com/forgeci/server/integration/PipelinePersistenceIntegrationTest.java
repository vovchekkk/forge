package com.forgeci.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.server.application.PipelineRunService;
import com.forgeci.server.application.PipelineService;
import com.forgeci.server.application.ProjectService;
import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.repository.JobLogRepository;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.repository.PipelineRunRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PipelinePersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private PipelineRunService runService;
    @Autowired
    private RunnerService runnerService;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobLogRepository jobLogRepository;
    @Autowired
    private PipelineRunRepository runRepository;

    private static final String CONFIG = """
            name: Java CI
            image: alpine
            jobs:
              test:
                commands: [echo test]
              lint:
                commands: [echo lint]
              build:
                needs: [test, lint]
                commands: [echo build]
            """;

    @Test
    void fullPersistenceFlowPersistsEntities() {
        UUID ownerId = createUser("owner-a@example.com");
        ProjectEntity project = projectService.create(ownerId, "repo-a", "https://example.com/repo-a.git", "main");
        assertNotNull(project.getId());

        PipelineEntity pipeline = pipelineService.create(ownerId, project.getId(), CONFIG);
        assertNotNull(pipeline.getId());
        assertEquals("Java CI", pipeline.getName());

        PipelineRunEntity run = runService.start(ownerId, pipeline.getId(), "main");
        assertEquals(PipelineRunStatus.QUEUED, run.getStatus());
        assertFalse(run.getConfigSnapshot().isBlank());

        List<JobEntity> jobs = jobRepository.findByPipelineRunId(run.getId());
        assertEquals(3, jobs.size());

        JobEntity test = jobs.stream().filter(j -> j.getName().equals("test")).findFirst().orElseThrow();
        JobEntity build = jobs.stream().filter(j -> j.getName().equals("build")).findFirst().orElseThrow();

        assertEquals(JobStatus.READY, test.getStatus());
        assertEquals(JobStatus.PENDING, build.getStatus());
        assertEquals("alpine", test.getImage());
        assertEquals(List.of("test", "lint"), JsonUtilHelper.parseStringList(build.getNeeds()));
    }

    @Test
    void claimMarksJobRunningAndAssignsRunner() {
        UUID ownerId = createUser("owner-b@example.com");
        ProjectEntity project = projectService.create(ownerId, "repo-b", "https://example.com/repo-b.git", "main");
        PipelineEntity pipeline = pipelineService.create(ownerId, project.getId(), CONFIG);
        PipelineRunEntity run = runService.start(ownerId, pipeline.getId(), null);

        RunnerService.RegistrationIssue issue =
                runnerService.createCredential(ownerId, "test-runner");
        UUID runnerId = issue.runner().getId();
        runnerService.register("test-runner", issue.registrationToken());

        Optional<com.forgeci.dto.JobClaim> claim = runnerService.claimNextJob(runnerId);
        assertTrue(claim.isPresent());

        JobEntity claimed = jobRepository.findById(claim.get().jobId()).orElseThrow();
        assertEquals(JobStatus.RUNNING, claimed.getStatus());
        assertEquals(runnerId, claimed.getRunner().getId());

        
        Optional<com.forgeci.dto.JobClaim> second = runnerService.claimNextJob(runnerId);
        assertTrue(second.isPresent());
        assertFalse(second.get().jobId().equals(claim.get().jobId()));

        
        PipelineRunEntity refreshed = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(PipelineRunStatus.RUNNING, refreshed.getStatus());
    }

    
    static final class JsonUtilHelper {
        static List<String> parseStringList(String json) {
            return com.forgeci.server.application.JsonUtil.readStringList(json);
        }
    }
}