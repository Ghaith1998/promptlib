package com.promptlib.domain.gallery;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.domain.gallery.model.GalleryItem;
import com.promptlib.domain.gallery.service.FavoriteService;
import com.promptlib.domain.gallery.service.GalleryService;
import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.port.CollectionRepository;
import com.promptlib.domain.library.service.CollectionService;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.MessageRole;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.service.PromptService;
import com.promptlib.domain.prompt.service.VersionService;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Page;
import com.promptlib.shared.error.NotFound;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GalleryTest {

    @Inject AccountService accounts;
    @Inject CategoryService categories;
    @Inject PromptService prompts;
    @Inject VersionService versions;
    @Inject GalleryService gallery;
    @Inject FavoriteService favorites;
    @Inject CollectionService collections;
    @Inject CollectionRepository collectionRepository;

    private CallerContext newCaller() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "g-" + UUID.randomUUID(), "g@example.com");
        return new CallerContext(user.id, user.keycloakSub, Set.of("user"));
    }

    private Prompt publishedPrompt(CallerContext owner) {
        Prompt prompt = prompts.create(owner, new PromptDraft("Review {{lang}}", "checklist",
                List.of(new MessageDraft(MessageRole.USER, "Review this {{lang}} code")),
                List.of(new com.promptlib.domain.prompt.model.VariableDefinition(
                        "lang", com.promptlib.domain.prompt.model.VariableType.STRING, true, null, null, List.of()))));
        versions.publish(prompt.id, 1, categories.listActive().getFirst().id, owner);
        return prompts.getOwned(owner, prompt.id);
    }

    @Test
    void galleryShowsOnlyPublishedVisiblePrompts() {
        CallerContext owner = newCaller();
        Prompt publicPrompt = publishedPrompt(owner);
        Prompt privatePrompt = prompts.create(owner, new PromptDraft("Private", null,
                List.of(new MessageDraft(MessageRole.USER, "secret")), List.of()));

        Page<GalleryItem> page = gallery.browse(null, null, 20);

        assertTrue(page.items().stream().anyMatch(i -> i.id().equals(publicPrompt.id)));
        assertTrue(page.items().stream().noneMatch(i -> i.id().equals(privatePrompt.id)));
    }

    @Test
    void forkCopiesContentIntoTheForkersDefaultCollection() {
        CallerContext owner = newCaller();
        CallerContext other = newCaller();
        Prompt source = publishedPrompt(owner);

        UUID forkId = gallery.fork(source.id, other);

        Prompt fork = prompts.getOwned(other, forkId);
        assertEquals(other.userId(), fork.ownerId);
        assertEquals(source.id, fork.forkOfPromptId);
        assertEquals(0, fork.copyCount);

        Collection def = collections.ensureDefault(other);
        assertTrue(collectionRepository.hasPrompt(def.id, fork.id));

        GalleryItem sourceRow = gallery.browse(null, null, 50).items().stream()
                .filter(i -> i.id().equals(source.id))
                .findFirst()
                .orElseThrow();
        assertEquals(1, sourceRow.copyCount());
    }

    @Test
    void forkOfAPrivatePromptIsNotPossible() {
        CallerContext owner = newCaller();
        CallerContext other = newCaller();
        Prompt privatePrompt = prompts.create(owner, new PromptDraft("Secret", null,
                List.of(new MessageDraft(MessageRole.USER, "no")), List.of()));

        assertThrows(NotFound.class, () -> gallery.fork(privatePrompt.id, other));
    }

    @Test
    void forkSurvivesSourceUnpublish() {
        CallerContext owner = newCaller();
        CallerContext other = newCaller();
        Prompt source = publishedPrompt(owner);
        UUID forkId = gallery.fork(source.id, other);

        versions.unpublish(source.id, owner);

        assertTrue(prompts.findVisible(forkId, other.userId()).isPresent());
    }

    @Test
    void favoritesAreIdempotentAndRemovable() {
        CallerContext owner = newCaller();
        CallerContext other = newCaller();
        Prompt source = publishedPrompt(owner);

        favorites.add(other, source.id);
        favorites.add(other, source.id);
        assertEquals(List.of(source.id), favorites.list(other));

        favorites.remove(other, source.id);
        assertTrue(favorites.list(other).isEmpty());
    }
}
