package com.promptlib.persistence;

import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.model.CollectionPrompt;
import com.promptlib.domain.library.port.CollectionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaCollectionRepository implements CollectionRepository {

    @Inject
    EntityManager em;

    @Override
    public Optional<Collection> findDefault(UUID ownerId) {
        return em.createQuery(
                        "select c from Collection c where c.ownerId = :owner and c.isDefault = true", Collection.class)
                .setParameter("owner", ownerId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Collection> findOwned(UUID id, UUID ownerId) {
        return em.createQuery(
                        "select c from Collection c where c.id = :id and c.ownerId = :owner", Collection.class)
                .setParameter("id", id)
                .setParameter("owner", ownerId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Collection> listOwned(UUID ownerId) {
        return em.createQuery(
                        "select c from Collection c where c.ownerId = :owner order by c.sortOrder, c.name", Collection.class)
                .setParameter("owner", ownerId)
                .getResultList();
    }

    @Override
    public boolean hasChildren(UUID collectionId) {
        return em.createQuery("select count(c) from Collection c where c.parentId = :id", Long.class)
                .setParameter("id", collectionId)
                .getSingleResult() > 0;
    }

    @Override
    public long countPrompts(UUID collectionId) {
        return em.createQuery("select count(cp) from CollectionPrompt cp where cp.collectionId = :id", Long.class)
                .setParameter("id", collectionId)
                .getSingleResult();
    }

    @Override
    public void persist(Collection collection) {
        em.persist(collection);
    }

    @Override
    public void delete(Collection collection) {
        em.remove(collection);
    }

    @Override
    public void addPrompt(CollectionPrompt membership) {
        em.persist(membership);
    }

    @Override
    public boolean hasPrompt(UUID collectionId, UUID promptId) {
        return em.createQuery(
                        "select count(cp) from CollectionPrompt cp where cp.collectionId = :c and cp.promptId = :p",
                        Long.class)
                .setParameter("c", collectionId)
                .setParameter("p", promptId)
                .getSingleResult() > 0;
    }

    @Override
    public void flush() {
        em.flush();
    }
}
