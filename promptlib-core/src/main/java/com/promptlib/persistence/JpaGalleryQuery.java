package com.promptlib.persistence;

import com.promptlib.domain.gallery.model.GalleryItem;
import com.promptlib.domain.gallery.port.GalleryQuery;
import com.promptlib.shared.Cursor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaGalleryQuery implements GalleryQuery {

    @Inject
    EntityManager em;

    @Override
    public List<GalleryItem> newest(UUID categoryId, Cursor cursor, int limit) {
        String jpql = """
                select new com.promptlib.domain.gallery.model.GalleryItem(
                    p.id, p.title, p.description, p.categoryId, p.copyCount, p.createdAt)
                from Prompt p
                where p.deletedAt is null
                  and p.visibility = com.promptlib.domain.prompt.model.Visibility.PUBLIC
                  and p.moderationState = com.promptlib.domain.prompt.model.ModerationState.VISIBLE
                """;
        if (categoryId != null) {
            jpql += " and p.categoryId = :category";
        }
        if (cursor != null) {
            jpql += " and (p.createdAt < :ts or (p.createdAt = :ts and p.id < :id))";
        }
        jpql += " order by p.createdAt desc, p.id desc";

        var query = em.createQuery(jpql, GalleryItem.class);
        if (categoryId != null) {
            query.setParameter("category", categoryId);
        }
        if (cursor != null) {
            query.setParameter("ts", cursor.timestamp()).setParameter("id", cursor.id());
        }
        return query.setMaxResults(limit).getResultList();
    }
}
