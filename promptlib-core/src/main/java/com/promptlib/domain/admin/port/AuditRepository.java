package com.promptlib.domain.admin.port;

import com.promptlib.domain.admin.model.AuditEntry;

import java.util.List;
import java.util.UUID;

public interface AuditRepository {

    void persist(AuditEntry entry);

    List<AuditEntry> listForTarget(String targetType, UUID targetId);
}
