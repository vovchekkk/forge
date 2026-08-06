package com.forgeci.server.repository;

import com.forgeci.model.PipelineRunStatus;
import com.forgeci.server.entity.PipelineRunEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRunRepository extends JpaRepository<PipelineRunEntity, UUID> {
    List<PipelineRunEntity> findByPipelineId(UUID pipelineId);

    List<PipelineRunEntity> findByStatus(PipelineRunStatus status);
}