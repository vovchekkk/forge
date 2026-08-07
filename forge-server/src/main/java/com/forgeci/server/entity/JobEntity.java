package com.forgeci.server.entity;

import com.forgeci.model.JobStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "pipeline_run_id", nullable = false)
    private PipelineRunEntity pipelineRun;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String commands;

    @Column(columnDefinition = "text")
    private String needs;

    @Column
    private Integer timeout;

    @Column(columnDefinition = "text")
    private String environment;

    @Column(nullable = false)
    private String image;

    @ManyToOne
    @JoinColumn(name = "runner_id")
    private RunnerEntity runner;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Version
    private Long version;

    public JobEntity() {}

    public JobEntity(PipelineRunEntity pipelineRun, String name, JobStatus status, String commands,
                     String needs, Integer timeout, String environment, String image) {
        this.pipelineRun = pipelineRun;
        this.name = name;
        this.status = status;
        this.commands = commands;
        this.needs = needs;
        this.timeout = timeout;
        this.environment = environment;
        this.image = image;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PipelineRunEntity getPipelineRun() { return pipelineRun; }
    public void setPipelineRun(PipelineRunEntity pipelineRun) { this.pipelineRun = pipelineRun; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getCommands() { return commands; }
    public void setCommands(String commands) { this.commands = commands; }
    public String getNeeds() { return needs; }
    public void setNeeds(String needs) { this.needs = needs; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public RunnerEntity getRunner() { return runner; }
    public void setRunner(RunnerEntity runner) { this.runner = runner; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}