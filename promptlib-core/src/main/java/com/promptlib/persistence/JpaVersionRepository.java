package com.promptlib.persistence;

import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.port.VersionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaVersionRepository implements VersionRepository {

    @Inject
    EntityManager em;

    @Override
    public int nextNumber(UUID promptId) {
        Integer max = em.createQuery(
                        "select max(v.versionNumber) from PromptVersion v where v.promptId = :id", Integer.class)
                .setParameter("id", promptId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }

    @Override
    public Optional<PromptVersion> findByNumber(UUID promptId, int versionNumber) {
        return em.createQuery(
                        "select v from PromptVersion v where v.promptId = :id and v.versionNumber = :n and v.deletedAt is null",
                        PromptVersion.class)
                .setParameter("id", promptId)
                .setParameter("n", versionNumber)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<PromptVersion> byId(UUID versionId) {
        return Optional.ofNullable(em.find(PromptVersion.class, versionId));
    }

    @Override
    public List<PromptVersion> list(UUID promptId) {
        return em.createQuery(
                        "select v from PromptVersion v where v.promptId = :id and v.deletedAt is null order by v.versionNumber desc",
                        PromptVersion.class)
                .setParameter("id", promptId)
                .getResultList();
    }

    @Override
    public void persist(PromptVersion version) {
        em.persist(version);
    }

    @Override
    public void persistMessage(PromptMessage message) {
        em.persist(message);
    }

    @Override
    public List<PromptMessage> messages(UUID versionId) {
        return em.createQuery(
                        "select m from PromptMessage m where m.versionId = :id order by m.position", PromptMessage.class)
                .setParameter("id", versionId)
                .getResultList();
    }
}
