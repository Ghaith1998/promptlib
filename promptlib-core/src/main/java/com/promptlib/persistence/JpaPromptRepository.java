package com.promptlib.persistence;

import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.shared.Cursor;
import com.promptlib.shared.error.NotFound;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaPromptRepository implements PromptRepository {

    @Inject
    EntityManager em;

    @Override
    public Optional<Prompt> findOwned(UUID id, UUID ownerId) {
        return em.createQuery(
                        "select p from Prompt p where p.id = :id and p.ownerId = :owner and p.deletedAt is null",
                        Prompt.class)
                .setParameter("id", id)
                .setParameter("owner", ownerId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Prompt> byId(UUID id) {
        return em.createQuery("select p from Prompt p where p.id = :id and p.deletedAt is null", Prompt.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Prompt> findPublic(UUID id) {
        return em.createQuery(
                        "select p from Prompt p where p.id = :id and p.deletedAt is null" +
                                " and p.visibility = com.promptlib.domain.prompt.model.Visibility.PUBLIC" +
                                " and p.moderationState = com.promptlib.domain.prompt.model.ModerationState.VISIBLE",
                        Prompt.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Prompt lockOwned(UUID id, UUID ownerId) {
        Prompt prompt = em.find(Prompt.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (prompt == null || prompt.deletedAt != null || !prompt.ownerId.equals(ownerId)) {
            throw new NotFound();
        }
        return prompt;
    }

    @Override
    public List<Prompt> pageOwned(UUID ownerId, Cursor cursor, String query, int limit) {
        String jpql = "select p from Prompt p where p.ownerId = :owner and p.deletedAt is null";
        if (query != null && !query.isBlank()) {
            jpql += " and (lower(p.title) like :q or lower(coalesce(p.description, '')) like :q)";
        }
        if (cursor != null) {
            jpql += " and (p.createdAt < :ts or (p.createdAt = :ts and p.id < :id))";
        }
        jpql += " order by p.createdAt desc, p.id desc";

        var typed = em.createQuery(jpql, Prompt.class).setParameter("owner", ownerId);
        if (query != null && !query.isBlank()) {
            typed.setParameter("q", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (cursor != null) {
            typed.setParameter("ts", cursor.timestamp()).setParameter("id", cursor.id());
        }
        return typed.setMaxResults(limit).getResultList();
    }

    @Override
    public void persist(Prompt prompt) {
        em.persist(prompt);
    }

    @Override
    public void incrementCopyCount(UUID promptId, java.time.Instant now) {
        em.createQuery("update Prompt p set p.copyCount = p.copyCount + 1, p.updatedAt = :now where p.id = :id")
                .setParameter("now", now)
                .setParameter("id", promptId)
                .executeUpdate();
    }

    @Override
    public void refresh(Prompt prompt) {
        em.refresh(prompt);
    }

    @Override
    public void deleteDependentRows(UUID promptId) {
        em.createNativeQuery("delete from prompt_tag where prompt_id = ?1")
                .setParameter(1, promptId).executeUpdate();
        em.createNativeQuery("delete from collection_prompt where prompt_id = ?1")
                .setParameter(1, promptId).executeUpdate();
        em.createNativeQuery("delete from favorite where prompt_id = ?1")
                .setParameter(1, promptId).executeUpdate();
        em.createNativeQuery("delete from prompt_search_document where prompt_id = ?1")
                .setParameter(1, promptId).executeUpdate();
    }
}
