package com.promptlib.persistence;

import com.promptlib.domain.library.model.PromptTag;
import com.promptlib.domain.library.model.Tag;
import com.promptlib.domain.library.port.TagRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaTagRepository implements TagRepository {

    @Inject
    EntityManager em;

    @Override
    public Optional<Tag> findOwned(UUID id, UUID ownerId) {
        return em.createQuery("select t from Tag t where t.id = :id and t.ownerId = :owner", Tag.class)
                .setParameter("id", id)
                .setParameter("owner", ownerId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Tag> findByName(UUID ownerId, String name) {
        return em.createQuery("select t from Tag t where t.ownerId = :owner and lower(t.name) = lower(:name)", Tag.class)
                .setParameter("owner", ownerId)
                .setParameter("name", name)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Tag> listOwned(UUID ownerId) {
        return em.createQuery("select t from Tag t where t.ownerId = :owner order by t.name", Tag.class)
                .setParameter("owner", ownerId)
                .getResultList();
    }

    @Override
    public long countUsage(UUID tagId) {
        return em.createQuery("select count(pt) from PromptTag pt where pt.tagId = :id", Long.class)
                .setParameter("id", tagId)
                .getSingleResult();
    }

    @Override
    public void persist(Tag tag) {
        em.persist(tag);
    }

    @Override
    public void delete(Tag tag) {
        em.remove(tag);
    }

    @Override
    public void attach(UUID promptId, UUID tagId) {
        PromptTag link = new PromptTag();
        link.promptId = promptId;
        link.tagId = tagId;
        em.persist(link);
    }

    @Override
    public void detachAll(UUID promptId) {
        em.createQuery("delete from PromptTag pt where pt.promptId = :id")
                .setParameter("id", promptId)
                .executeUpdate();
    }

    @Override
    public List<Tag> tagsOf(UUID promptId) {
        return em.createQuery(
                        "select t from Tag t, PromptTag pt where pt.tagId = t.id and pt.promptId = :id order by t.name",
                        Tag.class)
                .setParameter("id", promptId)
                .getResultList();
    }
}
