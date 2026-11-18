package com.promptlib;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.admin.service.AdminService;
import com.promptlib.domain.category.model.Category;
import com.promptlib.domain.category.service.CategoryAdminService;
import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.domain.export.service.ExportService;
import com.promptlib.domain.gallery.service.FavoriteService;
import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.service.CollectionService;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.MessageRole;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.service.PromptService;
import com.promptlib.domain.prompt.service.VersionService;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.error.Forbidden;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SurfaceTest {

    @Inject AccountService accounts;
    @Inject CategoryService categories;
    @Inject CategoryAdminService categoryAdmin;
    @Inject PromptService prompts;
    @Inject VersionService versions;
    @Inject CollectionService collections;
    @Inject FavoriteService favorites;
    @Inject AdminService admin;
    @Inject ExportService exports;

    private CallerContext caller(String role) {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "s-" + UUID.randomUUID(), "s@example.com");
        return new CallerContext(user.id, user.keycloakSub, Set.of(role));
    }

    private Prompt published(CallerContext owner) {
        Prompt prompt = prompts.create(owner, new PromptDraft("Export me", "desc",
                List.of(new MessageDraft(MessageRole.USER, "body text")), List.of()));
        versions.publish(prompt.id, 1, categories.listActive().getFirst().id, owner);
        return prompts.getOwned(owner, prompt.id);
    }

    @Test
    void favoritesAreListedWithPromptMetadata() {
        CallerContext owner = caller("user");
        CallerContext other = caller("user");
        Prompt source = published(owner);

        favorites.add(other, source.id);

        var rows = favorites.list(other);
        assertEquals(1, rows.size());
        assertEquals("Export me", rows.getFirst().title());
    }

    @Test
    void adminListShowsMetadataOnly() {
        CallerContext owner = caller("user");
        CallerContext adminUser = caller("admin");
        Prompt prompt = prompts.create(owner, new PromptDraft("Private one", null,
                List.of(new MessageDraft(MessageRole.USER, "secret body")), List.of()));

        var rows = admin.listPrompts(adminUser);

        assertTrue(rows.stream().anyMatch(r -> r.id().equals(prompt.id)));
        var row = rows.stream().filter(r -> r.id().equals(prompt.id)).findFirst().orElseThrow();
        assertEquals("Private one", row.title());
    }

    @Test
    void adminListRejectsNonAdmins() {
        assertThrows(Forbidden.class, () -> admin.listPrompts(caller("user")));
    }

    @Test
    void categoryAdminCreatesUpdatesAndDeactivates() {
        CallerContext adminUser = caller("admin");
        Category created = categoryAdmin.create(adminUser, "Data Science", "ml prompts", "#123456");

        assertEquals("data-science", created.slug);
        Category renamed = categoryAdmin.update(adminUser, created.id, "ML", null, null, 7);
        assertEquals("ML", renamed.name);
        assertEquals(7, renamed.sortOrder);

        Category deactivated = categoryAdmin.deactivate(adminUser, created.id);
        assertFalse(deactivated.active);
        assertTrue(categories.listActive().stream().noneMatch(c -> c.id.equals(created.id)));
    }

    @Test
    void categoryAdminRequiresAdmin() {
        assertThrows(Forbidden.class, () -> categoryAdmin.create(caller("user"), "Nope", null, null));
    }

    @Test
    void collectionCanBeRenamedAndReordered() {
        CallerContext owner = caller("user");
        Collection collection = collections.create(owner, "Old", null, null, null);

        Collection updated = collections.update(owner, collection.id, "New", "d", "#4f46e5", 5);

        assertEquals("New", updated.name);
        assertEquals("#4F46E5", updated.color);
        assertEquals(5, updated.sortOrder);
    }

    @Test
    void exportLibraryIsAVersionedSnapshot() {
        CallerContext owner = caller("user");
        Prompt prompt = published(owner);

        String json = String.join("", exports.exportLibrary(owner));

        assertTrue(json.contains("\"schemaVersion\" : \"1.0\""));
        assertTrue(json.contains("Export me"));
        assertTrue(json.contains("body text"));
        assertTrue(json.contains(prompt.id.toString()));
    }

    @Test
    void exportContainsOnlyTheCallersPrompts() {
        CallerContext alice = caller("user");
        CallerContext bob = caller("user");
        Prompt alicePrompt = published(alice);
        prompts.create(bob, new PromptDraft("Bob private", null,
                List.of(new MessageDraft(MessageRole.USER, "bob body")), List.of()));

        String bobExport = String.join("", exports.exportLibrary(bob));

        assertFalse(bobExport.contains(alicePrompt.title));
        assertTrue(bobExport.contains("Bob private"));
    }

    @Test
    void exportMarkdownIsReadable() {
        CallerContext owner = caller("user");
        Prompt prompt = published(owner);

        String md = String.join("", exports.exportMarkdown(owner, prompt.id));

        assertTrue(md.startsWith("# Export me"));
        assertTrue(md.contains("### USER"));
        assertTrue(md.contains("body text"));
    }
}
