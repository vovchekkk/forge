package com.forgeci.server.repository;

import com.forgeci.server.entity.PipelineEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {
    List<PipelineEntity> findByProjectId(UUID projectId);
}