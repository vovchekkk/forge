package com.forgeci.server.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.entity.UserEntity;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.repository.PipelineRunRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private PipelineRunRepository runRepository;

    private SchedulerService schedulerService;

    private PipelineRunEntity run;

    @BeforeEach
    void setUp() {
        schedulerService = new SchedulerService(jobRepository, runRepository);
        UserEntity owner = new UserEntity("owner@test.local", "hashed");
        ProjectEntity project = new ProjectEntity(owner, "proj", "https://example.com/repo.git", "main");
        PipelineEntity pipeline = new PipelineEntity(project, "ci", "config");
        run = new PipelineRunEntity(pipeline, PipelineRunStatus.QUEUED, "config", null);
        run.setId(UUID.randomUUID());
    }

    private JobEntity job(String name, JobStatus status, String needsJson, UUID id) {
        JobEntity job = new JobEntity(run, name, status, "[\"echo hi\"]", needsJson, null, "{}", "alpine");
        job.setId(id);
        return job;
    }

    @Test
    void pendingJobBecomesReadyWhenDependencySucceeds() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        JobEntity a = job("a", JobStatus.SUCCESS, "[]", aId);
        JobEntity b = job("b", JobStatus.PENDING, "[\"a\"]", bId);

        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(jobRepository.findByPipelineRunId(run.getId())).thenReturn(List.of(a, b));

        List<JobEntity> saved = new ArrayList<>();
        when(jobRepository.saveAll(anyList())).thenAnswer(inv -> {
            saved.addAll(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.updateRun(run.getId());

        assertEquals(JobStatus.READY, b.getStatus());
        
        assertEquals(PipelineRunStatus.QUEUED, run.getStatus());
    }

    @Test
    void dependentJobIsSkippedWhenDependencyFails() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        JobEntity a = job("a", JobStatus.FAILED, "[]", aId);
        JobEntity b = job("b", JobStatus.PENDING, "[\"a\"]", bId);

        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(jobRepository.findByPipelineRunId(run.getId())).thenReturn(List.of(a, b));

        List<JobEntity> saved = new ArrayList<>();
        when(jobRepository.saveAll(anyList())).thenAnswer(inv -> {
            saved.addAll(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.updateRun(run.getId());

        assertEquals(JobStatus.SKIPPED, b.getStatus());
        assertEquals(PipelineRunStatus.FAILED, run.getStatus());
        assertTrue(run.getFinishedAt() != null);
    }

    @Test
    void independentBranchStillRunsWhenSiblingFails() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        JobEntity a = job("a", JobStatus.FAILED, "[]", aId);
        JobEntity b = job("b", JobStatus.PENDING, "[]", bId);
        JobEntity c = job("c", JobStatus.PENDING, "[\"a\"]", cId);

        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(jobRepository.findByPipelineRunId(run.getId())).thenReturn(List.of(a, b, c));

        List<JobEntity> saved = new ArrayList<>();
        when(jobRepository.saveAll(anyList())).thenAnswer(inv -> {
            saved.addAll(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.updateRun(run.getId());

        assertEquals(JobStatus.READY, b.getStatus());
        assertEquals(JobStatus.SKIPPED, c.getStatus());
        
        assertEquals(PipelineRunStatus.QUEUED, run.getStatus());
    }

    @Test
    void allSuccessFinalizesRun() {
        UUID aId = UUID.randomUUID();
        JobEntity a = job("a", JobStatus.SUCCESS, "[]", aId);

        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(jobRepository.findByPipelineRunId(run.getId())).thenReturn(List.of(a));

        schedulerService.updateRun(run.getId());

        assertEquals(PipelineRunStatus.SUCCESS, run.getStatus());
        assertTrue(run.getFinishedAt() != null);
    }
}