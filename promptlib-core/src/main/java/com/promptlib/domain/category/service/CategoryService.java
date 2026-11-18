package com.promptlib.domain.category.service;

import com.promptlib.domain.category.model.Category;
import com.promptlib.domain.category.port.CategoryRepository;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class CategoryService {

    private final CategoryRepository categories;

    @Inject
    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    public List<Category> listActive() {
        return categories.listActive();
    }

    /** The category a publisher selected must exist and be active. */
    public Category requireActive(UUID categoryId) {
        if (categoryId == null) {
            throw new ValidationFailed(ProblemCode.CATEGORY_REQUIRED);
        }
        return categories.findActiveById(categoryId)
                .orElseThrow(() -> new ValidationFailed(ProblemCode.CATEGORY_REQUIRED));
    }
}
