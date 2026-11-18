package com.promptlib.persistence;

import com.promptlib.domain.gallery.model.Favorite;
import com.promptlib.domain.gallery.port.FavoriteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaFavoriteRepository implements FavoriteRepository {

    @Inject
    EntityManager em;

    @Override
    public boolean exists(UUID userId, UUID promptId) {
        return em.find(Favorite.class, new Favorite.Key(userId, promptId)) != null;
    }

    @Override
    public void add(UUID userId, UUID promptId, Instant now) {
        Favorite favorite = new Favorite();
        favorite.userId = userId;
        favorite.promptId = promptId;
        favorite.createdAt = now;
        em.persist(favorite);
    }

    @Override
    public void remove(UUID userId, UUID promptId) {
        Favorite favorite = em.find(Favorite.class, new Favorite.Key(userId, promptId));
        if (favorite != null) {
            em.remove(favorite);
        }
    }

    @Override
    public List<UUID> list(UUID userId) {
        return em.createQuery(
                        "select f.promptId from Favorite f where f.userId = :user order by f.createdAt desc", UUID.class)
                .setParameter("user", userId)
                .getResultList();
    }
}
