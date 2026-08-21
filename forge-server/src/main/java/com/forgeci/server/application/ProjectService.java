package com.forgeci.server.application;

import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.UserEntity;
import com.forgeci.server.repository.PipelineRepository;
import com.forgeci.server.repository.ProjectRepository;
import com.forgeci.server.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final PipelineRepository pipelineRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, PipelineRepository pipelineRepository,
                          UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.pipelineRepository = pipelineRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectEntity create(UUID ownerId, String name, String repositoryUrl, String repositoryBranch) {
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
        String branch = repositoryBranch == null || repositoryBranch.isBlank() ? "main" : repositoryBranch;
        ProjectEntity project = new ProjectEntity(owner, name, repositoryUrl, branch);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> findByOwner(UUID ownerId) {
        return projectRepository.findByOwner_Id(ownerId);
    }

    @Transactional(readOnly = true)
    public ProjectEntity getOwned(UUID ownerId, UUID id) {
        ProjectEntity project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
        if (project.getOwnerId() == null || !project.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Project not found: " + id);
        }
        return project;
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        ProjectEntity project = getOwned(ownerId, id);
        pipelineRepository.deleteAll(pipelineRepository.findByProjectId(id));
        projectRepository.delete(project);
    }
}