package com.forgeci.server.application;

import com.forgeci.server.entity.AuditLogEntity;
import com.forgeci.server.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only structured audit log. Never logs passwords, JWTs or raw tokens —
 * only event names, actor identity and small non-sensitive details.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUserEvent(String event, UUID userId, String details) {
        record(event, userId, "user", details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRunnerEvent(String event, UUID runnerId, String details) {
        record(event, runnerId, "runner", details);
    }

    private void record(String event, UUID actorId, String actorType, String details) {
        auditLogRepository.save(new AuditLogEntity(event, actorId, actorType, details));
    }
}