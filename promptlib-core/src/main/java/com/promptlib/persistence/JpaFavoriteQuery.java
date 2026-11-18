package com.promptlib.persistence;

import com.promptlib.domain.gallery.model.FavoriteRow;
import com.promptlib.domain.gallery.port.FavoriteQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaFavoriteQuery implements FavoriteQuery {

    @Inject
    EntityManager em;

    @Override
    public List<FavoriteRow> list(UUID userId) {
        return em.createQuery("""
                        select new com.promptlib.domain.gallery.model.FavoriteRow(
                            p.id, p.title, p.description, p.categoryId, p.copyCount, p.createdAt)
                        from Favorite f, Prompt p
                        where f.promptId = p.id and f.userId = :user
                          and p.deletedAt is null
                          and (p.ownerId = :user
                               or (p.visibility = com.promptlib.domain.prompt.model.Visibility.PUBLIC
                                   and p.moderationState = com.promptlib.domain.prompt.model.ModerationState.VISIBLE))
                        order by f.createdAt desc
                        """, FavoriteRow.class)
                .setParameter("user", userId)
                .getResultList();
    }
}
