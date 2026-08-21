package com.forgeci.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "repository_url", nullable = false, columnDefinition = "text")
    private String repositoryUrl;

    @Column(name = "repository_branch")
    private String repositoryBranch;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectEntity() {}

    public ProjectEntity(UserEntity owner, String name, String repositoryUrl, String repositoryBranch) {
        this.owner = owner;
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.repositoryBranch = repositoryBranch;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UserEntity getOwner() { return owner; }
    public void setOwner(UserEntity owner) { this.owner = owner; }
    public UUID getOwnerId() { return owner == null ? null : owner.getId(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getRepositoryBranch() { return repositoryBranch; }
    public void setRepositoryBranch(String repositoryBranch) { this.repositoryBranch = repositoryBranch; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}