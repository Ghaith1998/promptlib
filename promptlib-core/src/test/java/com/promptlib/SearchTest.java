package com.promptlib;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.domain.gallery.service.GalleryService;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.MessageRole;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.service.PromptService;
import com.promptlib.domain.prompt.service.VersionService;
import com.promptlib.shared.CallerContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SearchTest {

    @Inject AccountService accounts;
    @Inject CategoryService categories;
    @Inject PromptService prompts;
    @Inject VersionService versions;
    @Inject GalleryService gallery;

    private CallerContext caller() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "q-" + UUID.randomUUID(), "q@example.com");
        return new CallerContext(user.id, user.keycloakSub, Set.of("user"));
    }

    private Prompt create(CallerContext caller, String title, String description) {
        return prompts.create(caller, new PromptDraft(title, description,
                List.of(new MessageDraft(MessageRole.USER, "text")), List.of()));
    }

    @Test
    void ownerListFiltersByTitleCaseInsensitively() {
        CallerContext caller = caller();
        create(caller, "Alpha guide", null);
        create(caller, "Beta guide", "contains ALPHA in the description");
        create(caller, "Gamma", null);

        var page = prompts.listOwned(caller, null, "alpha", 20);

        assertEquals(2, page.items().size());
        assertTrue(page.items().stream().allMatch(p -> p.title.toLowerCase().contains("alpha")
                || (p.description != null && p.description.toLowerCase().contains("alpha"))));
    }

    @Test
    void ownerSearchComposesWithCursor() {
        CallerContext caller = caller();
        for (int i = 0; i < 3; i++) {
            create(caller, "Match " + i, null);
        }
        create(caller, "Other", null);

        var first = prompts.listOwned(caller, null, "match", 2);
        assertEquals(2, first.items().size());
        assertNotNull(first.nextCursor());

        var second = prompts.listOwned(caller, first.nextCursor(), "match", 2);
        assertEquals(1, second.items().size());
        assertNull(second.nextCursor());
    }

    @Test
    void gallerySearchReturnsOnlyPublishedMatches() {
        CallerContext owner = caller();
        Prompt alpha = create(owner, "Alpha public", null);
        Prompt beta = create(owner, "Beta public", null);
        create(owner, "Alpha private", null);
        var categoryId = categories.listActive().getFirst().id;
        versions.publish(alpha.id, 1, categoryId, owner);
        versions.publish(beta.id, 1, categoryId, owner);

        var results = gallery.browse(null, "alpha", null, 20).items();

        assertEquals(1, results.size());
        assertEquals(alpha.id, results.getFirst().id());
    }
}
