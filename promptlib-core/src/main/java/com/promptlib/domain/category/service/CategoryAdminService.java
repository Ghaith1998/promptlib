package com.promptlib.domain.category.service;

import com.promptlib.domain.category.model.Category;
import com.promptlib.domain.category.port.CategoryRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Ids;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.Forbidden;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.error.ValidationFailed;
import com.promptlib.shared.validation.Colour;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/** Taxonomy writes: create, rename, deactivate. There is no hard delete. */
@ApplicationScoped
public class CategoryAdminService {

    private final CategoryRepository categories;
    private final Clock clock;

    @Inject
    public CategoryAdminService(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public Category create(CallerContext admin, String name, String description, String color) {
        requireAdmin(admin);
        if (name == null || name.isBlank()) {
            throw new ValidationFailed(ProblemCode.VALIDATION_FAILED);
        }
        String slug = slugify(name);
        if (categories.findBySlug(slug).isPresent()) {
            throw new Conflict(ProblemCode.CONFLICT);
        }
        Category category = new Category();
        category.id = Ids.newId();
        category.slug = slug;
        category.name = name.trim();
        category.description = description;
        category.color = Colour.normalise(color);
        category.active = true;
        category.sortOrder = 100;
        category.createdAt = clock.instant();
        category.updatedAt = category.createdAt;
        categories.persist(category);
        return category;
    }

    @Transactional
    public Category update(CallerContext admin, UUID id, String name, String description,
                           String color, Integer sortOrder) {
        requireAdmin(admin);
        Category category = categories.byId(id).orElseThrow(NotFound::new);
        if (name != null && !name.isBlank()) {
            category.name = name.trim();
        }
        if (description != null) {
            category.description = description;
        }
        if (color != null) {
            category.color = Colour.normalise(color);
        }
        if (sortOrder != null) {
            category.sortOrder = sortOrder;
        }
        category.updatedAt = clock.instant();
        return category;
    }

    @Transactional
    public Category deactivate(CallerContext admin, UUID id) {
        requireAdmin(admin);
        Category category = categories.byId(id).orElseThrow(NotFound::new);
        category.active = false;
        category.updatedAt = clock.instant();
        return category;
    }

    private void requireAdmin(CallerContext caller) {
        if (!caller.isAdmin()) {
            throw new Forbidden();
        }
    }

    static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.length() > 64 ? slug.substring(0, 64) : slug;
    }
}
