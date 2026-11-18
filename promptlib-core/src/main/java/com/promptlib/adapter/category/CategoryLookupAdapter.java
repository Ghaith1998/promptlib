package com.promptlib.adapter.category;

import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.shared.port.CategoryLookup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class CategoryLookupAdapter implements CategoryLookup {

    private final CategoryService categories;

    @Inject
    public CategoryLookupAdapter(CategoryService categories) {
        this.categories = categories;
    }

    @Override
    public void requireActive(UUID categoryId) {
        categories.requireActive(categoryId);
    }
}
