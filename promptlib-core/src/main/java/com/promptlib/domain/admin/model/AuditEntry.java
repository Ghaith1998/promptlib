package com.promptlib.domain.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of a privileged action. Survives deletion of its target. */
@Entity
@Table(name = "admin_audit")
public class AuditEntry {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "actor_id", nullable = false)
    public UUID actorId;

    @Column(name = "action", nullable = false)
    public String action;

    @Column(name = "target_type", nullable = false)
    public String targetType;

    @Column(name = "target_id", nullable = false)
    public UUID targetId;

    @Column(name = "reason")
    public String reason;

    @Column(name = "request_id")
    public String requestId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
