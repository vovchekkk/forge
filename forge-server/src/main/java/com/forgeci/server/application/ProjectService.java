package com.forgeci.server.application;

import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.repository.PipelineRepository;
import com.forgeci.server.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final PipelineRepository pipelineRepository;

    public ProjectService(ProjectRepository projectRepository, PipelineRepository pipelineRepository) {
        this.projectRepository = projectRepository;
        this.pipelineRepository = pipelineRepository;
    }

    @Transactional
    public ProjectEntity create(String name, String repositoryUrl, String repositoryBranch) {
        String branch = repositoryBranch == null || repositoryBranch.isBlank() ? "main" : repositoryBranch;
        ProjectEntity project = new ProjectEntity(name, repositoryUrl, branch);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> findAll() {
        return projectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ProjectEntity get(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new NotFoundException("Project not found: " + id);
        }
        pipelineRepository.deleteAll(pipelineRepository.findByProjectId(id));
        projectRepository.deleteById(id);
    }
}