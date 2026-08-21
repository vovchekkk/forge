package com.forgeci.server.entity;

import com.forgeci.model.RunnerStatus;
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
@Table(name = "runners")
public class RunnerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "credential_hash", nullable = false, unique = true)
    private String credentialHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RunnerStatus status;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RunnerEntity() {}

    public RunnerEntity(String name, String credentialHash, RunnerStatus status, UserEntity owner) {
        this.name = name;
        this.credentialHash = credentialHash;
        this.status = status;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCredentialHash() { return credentialHash; }
    public void setCredentialHash(String credentialHash) { this.credentialHash = credentialHash; }
    public RunnerStatus getStatus() { return status; }
    public void setStatus(RunnerStatus status) { this.status = status; }
    public UserEntity getOwner() { return owner; }
    public void setOwner(UserEntity owner) { this.owner = owner; }
    public UUID getOwnerId() { return owner == null ? null : owner.getId(); }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}