package com.forgeci.server.web.controller;

import com.forgeci.server.application.PipelineRunService;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.security.SecurityUtils;
import com.forgeci.server.web.dto.PipelineRunResponse;
import com.forgeci.server.web.dto.StartRunRequest;
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
public class PipelineRunController {

    private final PipelineRunService runService;

    public PipelineRunController(PipelineRunService runService) {
        this.runService = runService;
    }

    @PostMapping("/pipelines/{pipelineId}/runs")
    public ResponseEntity<PipelineRunResponse> start(@PathVariable UUID pipelineId,
                                                     @RequestBody(required = false) StartRunRequest request) {
        UUID ownerId = SecurityUtils.requireUserId();
        String revision = request == null ? null : request.revision();
        PipelineRunEntity run = runService.start(ownerId, pipelineId, revision);
        return ResponseEntity.created(URI.create("/api/pipeline-runs/" + run.getId()))
                .body(toResponse(run));
    }

    @GetMapping("/pipelines/{pipelineId}/runs")
    public List<PipelineRunResponse> listByPipeline(@PathVariable UUID pipelineId) {
        UUID ownerId = SecurityUtils.requireUserId();
        return runService.listByPipeline(ownerId, pipelineId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/pipeline-runs/{id}")
    public PipelineRunResponse get(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireUserId();
        return toResponse(runService.get(ownerId, id));
    }

    @PostMapping("/pipeline-runs/{id}/cancel")
    public PipelineRunResponse cancel(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireUserId();
        return toResponse(runService.cancel(ownerId, id));
    }

    private PipelineRunResponse toResponse(PipelineRunEntity entity) {
        return new PipelineRunResponse(entity.getId(), entity.getPipeline().getId(), entity.getStatus(),
                entity.getRevision(), entity.getCreatedAt(), entity.getStartedAt(), entity.getFinishedAt());
    }
}