package com.promptlib.domain.admin.service;

import com.promptlib.domain.admin.model.AuditEntry;
import com.promptlib.domain.admin.port.AuditRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class AuditWriter {

    private final AuditRepository audit;
    private final Clock clock;

    @Inject
    public AuditWriter(AuditRepository audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    /** Must run inside the caller's transaction: no audit row, no action. */
    public void record(CallerContext actor, String action, String targetType, UUID targetId,
                       String reason, String requestId) {
        AuditEntry entry = new AuditEntry();
        entry.id = Ids.newId();
        entry.actorId = actor.userId();
        entry.action = action;
        entry.targetType = targetType;
        entry.targetId = targetId;
        entry.reason = reason;
        entry.requestId = requestId;
        entry.createdAt = clock.instant();
        audit.persist(entry);
    }
}
