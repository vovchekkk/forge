package com.forgeci.server.repository;

import com.forgeci.server.entity.JobLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobLogRepository extends JpaRepository<JobLogEntity, UUID> {
    List<JobLogEntity> findByJobIdOrderByCreatedAtAsc(UUID jobId);

    void deleteByJobId(UUID jobId);
}