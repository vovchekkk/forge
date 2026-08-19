package com.forgeci.server.repository;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.server.entity.JobEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    List<JobEntity> findByPipelineRunId(UUID pipelineRunId);

    @Query("select j from JobEntity j where j.pipelineRun.id = :runId and j.name in :names")
    List<JobEntity> findJobsByNames(@Param("runId") UUID runId, @Param("names") List<String> names);

    @Query("select count(j) from JobEntity j where j.pipelineRun.id = :runId and j.status = :status")
    long countByPipelineRunIdAndStatus(@Param("runId") UUID runId, @Param("status") JobStatus status);

    @Query("select j from JobEntity j where j.pipelineRun.id = :runId and j.status in :statuses")
    List<JobEntity> findByPipelineRunIdAndStatuses(@Param("runId") UUID runId, @Param("statuses") List<JobStatus> statuses);

    @Query("select j from JobEntity j where j.runner.id = :runnerId and j.status in :statuses")
    List<JobEntity> findByRunnerIdAndStatuses(@Param("runnerId") UUID runnerId, @Param("statuses") List<JobStatus> statuses);

    @Query("select j from JobEntity j where j.pipelineRun.status = :runStatus and j.status = :status and j.runner is null")
    List<JobEntity> findReadyUnexecutedJobs(@Param("runStatus") PipelineRunStatus runStatus,
                                            @Param("status") JobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobEntity j where j.id = :id")
    Optional<JobEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select j.* from jobs j " +
            "join pipeline_runs pr on pr.id = j.pipeline_run_id " +
            "where pr.status in ('QUEUED','RUNNING') " +
            "and j.status = 'READY' and j.runner_id is null " +
            "order by j.created_at asc " +
            "for update of j skip locked limit 1", nativeQuery = true)
    Optional<JobEntity> claimNextJob();
}