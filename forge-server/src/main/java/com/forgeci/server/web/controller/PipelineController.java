package com.forgeci.server.web.controller;

import com.forgeci.server.application.PipelineService;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.web.dto.CreatePipelineRequest;
import com.forgeci.server.web.dto.PipelineResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PipelineController {

    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/projects/{projectId}/pipelines")
    public ResponseEntity<PipelineResponse> create(@PathVariable UUID projectId,
                                                   @Valid @RequestBody CreatePipelineRequest request) {
        PipelineEntity pipeline = pipelineService.create(projectId, request.config());
        return ResponseEntity.created(URI.create("/api/pipelines/" + pipeline.getId()))
                .body(toResponse(pipeline));
    }

    @GetMapping("/projects/{projectId}/pipelines")
    public List<PipelineResponse> listByProject(@PathVariable UUID projectId) {
        return pipelineService.listByProject(projectId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/pipelines/{id}")
    public PipelineResponse get(@PathVariable UUID id) {
        return toResponse(pipelineService.get(id));
    }

    private PipelineResponse toResponse(PipelineEntity entity) {
        return new PipelineResponse(entity.getId(), entity.getProject().getId(), entity.getName(),
                entity.getConfig(), entity.getCreatedAt());
    }
}