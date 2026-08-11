package com.forgeci.server.web.controller;

import com.forgeci.server.application.ProjectService;
import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.web.dto.CreateProjectRequest;
import com.forgeci.server.web.dto.ProjectResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        ProjectEntity project = projectService.create(request.name(), request.repositoryUrl(), request.repositoryBranch());
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
                .body(toResponse(project));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return toResponse(projectService.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ProjectResponse toResponse(ProjectEntity entity) {
        return new ProjectResponse(entity.getId(), entity.getName(), entity.getRepositoryUrl(),
                entity.getRepositoryBranch(), entity.getCreatedAt());
    }
}