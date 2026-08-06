package com.forgeci.server.entity;

import com.forgeci.model.PipelineRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_runs")
public class PipelineRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id", nullable = false)
    private PipelineEntity pipeline;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PipelineRunStatus status;

    @Column(name = "config_snapshot", nullable = false, columnDefinition = "text")
    private String configSnapshot;

    @Column
    private String revision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public PipelineRunEntity() {}

    public PipelineRunEntity(PipelineEntity pipeline, PipelineRunStatus status, String configSnapshot, String revision) {
        this.pipeline = pipeline;
        this.status = status;
        this.configSnapshot = configSnapshot;
        this.revision = revision;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PipelineEntity getPipeline() { return pipeline; }
    public void setPipeline(PipelineEntity pipeline) { this.pipeline = pipeline; }
    public PipelineRunStatus getStatus() { return status; }
    public void setStatus(PipelineRunStatus status) { this.status = status; }
    public String getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(String configSnapshot) { this.configSnapshot = configSnapshot; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}