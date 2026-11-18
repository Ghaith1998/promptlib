package com.promptlib.persistence;

import com.promptlib.domain.category.model.Category;
import com.promptlib.domain.category.port.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaCategoryRepository implements CategoryRepository {

    @Inject
    EntityManager em;

    @Override
    public List<Category> listActive() {
        return em.createQuery(
                        "select c from Category c where c.active = true order by c.sortOrder, c.name",
                        Category.class)
                .getResultList();
    }

    @Override
    public Optional<Category> byId(UUID id) {
        return Optional.ofNullable(em.find(Category.class, id));
    }

    @Override
    public Optional<Category> findActiveById(UUID id) {
        return em.createQuery(
                        "select c from Category c where c.id = :id and c.active = true", Category.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return em.createQuery("select c from Category c where c.slug = :slug", Category.class)
                .setParameter("slug", slug)
                .getResultStream()
                .findFirst();
    }

    @Override
    public void persist(Category category) {
        em.persist(category);
    }
}
