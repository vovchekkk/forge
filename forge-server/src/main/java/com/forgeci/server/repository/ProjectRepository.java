package com.forgeci.server.repository;

import com.forgeci.server.entity.ProjectEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    Optional<ProjectEntity> findByName(String name);

    List<ProjectEntity> findByOwner_Id(UUID ownerId);
}