package com.promptlib.domain.category;

import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.shared.error.ValidationFailed;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CategoryServiceTest {

    @Inject
    CategoryService categories;

    @Test
    void seedProvidesSevenActiveCategories() {
        var active = categories.listActive();
        assertEquals(7, active.size());
        assertEquals("coding", active.getFirst().slug);
    }

    @Test
    void unknownCategoryIsRejected() {
        assertThrows(ValidationFailed.class, () -> categories.requireActive(UUID.randomUUID()));
    }

    @Test
    void nullCategoryIsRejected() {
        assertThrows(ValidationFailed.class, () -> categories.requireActive(null));
    }
}
