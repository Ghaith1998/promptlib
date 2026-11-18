package com.promptlib.persistence;

import com.promptlib.domain.admin.model.AuditEntry;
import com.promptlib.domain.admin.port.AuditRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaAuditRepository implements AuditRepository {

    @Inject
    EntityManager em;

    @Override
    public void persist(AuditEntry entry) {
        em.persist(entry);
    }

    @Override
    public List<AuditEntry> listForTarget(String targetType, UUID targetId) {
        return em.createQuery(
                        "select a from AuditEntry a where a.targetType = :type and a.targetId = :id order by a.createdAt",
                        AuditEntry.class)
                .setParameter("type", targetType)
                .setParameter("id", targetId)
                .getResultList();
    }
}
