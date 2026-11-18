package com.promptlib.domain.category.port;

import com.promptlib.domain.category.model.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    List<Category> listActive();

    Optional<Category> byId(UUID id);

    Optional<Category> findActiveById(UUID id);

    Optional<Category> findBySlug(String slug);

    void persist(Category category);
}
