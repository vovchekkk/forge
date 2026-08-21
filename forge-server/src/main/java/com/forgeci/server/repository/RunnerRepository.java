package com.forgeci.server.repository;

import com.forgeci.model.RunnerStatus;
import com.forgeci.server.entity.RunnerEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RunnerRepository extends JpaRepository<RunnerEntity, UUID> {
    Optional<RunnerEntity> findByCredentialHash(String credentialHash);

    List<RunnerEntity> findByOwner_Id(UUID ownerId);

    @Query("select r from RunnerEntity r where r.status <> 'OFFLINE' and r.lastHeartbeatAt < :threshold")
    List<RunnerEntity> findStaleRunners(@Param("threshold") Instant threshold);

    List<RunnerEntity> findByStatus(RunnerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RunnerEntity r where r.id = :id")
    Optional<RunnerEntity> findByIdForUpdate(@Param("id") UUID id);
}