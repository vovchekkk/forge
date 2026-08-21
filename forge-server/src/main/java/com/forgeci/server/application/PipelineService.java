package com.forgeci.server.application;

import com.forgeci.model.JobDefinition;
import com.forgeci.model.PipelineDefinition;
import com.forgeci.parser.PipelineParser;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.repository.PipelineRepository;
import com.forgeci.server.repository.ProjectRepository;
import com.forgeci.validator.PipelineValidator;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final ProjectRepository projectRepository;

    public PipelineService(PipelineRepository pipelineRepository, ProjectRepository projectRepository) {
        this.pipelineRepository = pipelineRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public PipelineEntity create(UUID ownerId, UUID projectId, String config) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (project.getOwnerId() == null || !project.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
        PipelineDefinition definition = parseAndValidate(config);
        String name = definition.getName() == null || definition.getName().isBlank()
                ? "pipeline"
                : definition.getName();
        PipelineEntity pipeline = new PipelineEntity(project, name, config);
        return pipelineRepository.save(pipeline);
    }

    @Transactional(readOnly = true)
    public List<PipelineEntity> listByProject(UUID ownerId, UUID projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (project.getOwnerId() == null || !project.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
        return pipelineRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public PipelineEntity get(UUID ownerId, UUID id) {
        PipelineEntity pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pipeline not found: " + id));
        UUID projectOwnerId = pipeline.getProject().getOwnerId();
        if (projectOwnerId == null || !projectOwnerId.equals(ownerId)) {
            throw new NotFoundException("Pipeline not found: " + id);
        }
        return pipeline;
    }

    /** Parse and validate the raw YAML config. Throws {@link InvalidPipelineException} on failure. */
    public PipelineDefinition parseAndValidate(String config) {
        PipelineDefinition definition;
        try {
            definition = PipelineParser.parse(config);
        } catch (IOException e) {
            throw new InvalidPipelineException(List.of("Malformed YAML: " + e.getMessage()));
        }
        List<String> errors = PipelineValidator.validate(definition);
        if (!errors.isEmpty()) {
            throw new InvalidPipelineException(errors);
        }
        return definition;
    }
}