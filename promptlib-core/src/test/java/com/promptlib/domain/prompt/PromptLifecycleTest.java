package com.promptlib.domain.prompt;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.category.model.Category;
import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.MessageRole;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.model.VariableDefinition;
import com.promptlib.domain.prompt.model.VariableType;
import com.promptlib.domain.prompt.model.Visibility;
import com.promptlib.domain.prompt.service.PromptService;
import com.promptlib.domain.prompt.service.VersionService;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Page;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.ValidationFailed;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PromptLifecycleTest {

    @Inject
    AccountService accounts;
    @Inject
    CategoryService categories;
    @Inject
    PromptService prompts;
    @Inject
    VersionService versions;

    private CallerContext newCaller() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "user-" + UUID.randomUUID(),
                "user@example.com");
        return new CallerContext(user.id, user.keycloakSub, Set.of("user"));
    }

    private PromptDraft draft(String title) {
        return new PromptDraft(title, "desc",
                List.of(new MessageDraft(MessageRole.USER, "Write about {{topic}}")),
                List.of(new VariableDefinition("topic", VariableType.STRING, true, null, null, List.of())));
    }

    @Test
    void createBuildsPromptAndFirstVersion() {
        CallerContext caller = newCaller();
        Prompt prompt = prompts.create(caller, draft("Outline"));

        assertNotNull(prompt.id);
        assertNotNull(prompt.latestVersionId);
        assertEquals(Visibility.PRIVATE, prompt.visibility);
        assertEquals(1, versions.list(prompt.id, caller).size());
    }

    @Test
    void placeholderMismatchIsRejected() {
        CallerContext caller = newCaller();
        PromptDraft broken = new PromptDraft("Broken", null,
                List.of(new MessageDraft(MessageRole.USER, "Hello {{name}}")), List.of());

        ValidationFailed ex = assertThrows(ValidationFailed.class, () -> prompts.create(caller, broken));
        assertEquals(ProblemCode.VARIABLE_MISMATCH, ex.code());
    }

    @Test
    void publishingPinsTheVersionAndNewVersionsDoNotLeak() {
        CallerContext caller = newCaller();
        Prompt prompt = prompts.create(caller, draft("Pinned"));
        Category category = categories.listActive().getFirst();

        Prompt published = versions.publish(prompt.id, 1, category.id, caller);
        assertEquals(Visibility.PUBLIC, published.visibility);
        UUID pinned = published.publishedVersionId;

        PromptVersion v2 = versions.createVersion(prompt.id, caller, "second",
                List.of(new MessageDraft(MessageRole.USER, "different text")), List.of());

        Prompt after = prompts.getOwned(caller, prompt.id);
        assertEquals(pinned, after.publishedVersionId);
        assertEquals(v2.id, after.latestVersionId);
    }

    @Test
    void publishingRequiresAnActiveCategory() {
        CallerContext caller = newCaller();
        Prompt prompt = prompts.create(caller, draft("No category"));

        assertThrows(ValidationFailed.class, () -> versions.publish(prompt.id, 1, UUID.randomUUID(), caller));
    }

    @Test
    void publishedPromptCannotBeDeleted() {
        CallerContext caller = newCaller();
        Prompt prompt = prompts.create(caller, draft("Public"));
        versions.publish(prompt.id, 1, categories.listActive().getFirst().id, caller);

        Conflict ex = assertThrows(Conflict.class, () -> prompts.delete(caller, prompt.id));
        assertEquals(ProblemCode.PROMPT_PUBLISHED, ex.code());
    }

    @Test
    void unpublishedPromptIsHiddenAfterDelete() {
        CallerContext caller = newCaller();
        Prompt prompt = prompts.create(caller, draft("Private"));

        prompts.delete(caller, prompt.id);

        assertTrue(prompts.findVisible(prompt.id, caller.userId()).isEmpty());
        assertTrue(prompts.listOwned(caller, null, null, 20).items().isEmpty());
    }

    @Test
    void listUsesCursorPagination() {
        CallerContext caller = newCaller();
        prompts.create(caller, draft("one"));
        prompts.create(caller, draft("two"));
        prompts.create(caller, draft("three"));

        Page<Prompt> first = prompts.listOwned(caller, null, null, 2);
        assertEquals(2, first.items().size());
        assertNotNull(first.nextCursor());

        Page<Prompt> second = prompts.listOwned(caller, first.nextCursor(), null, 2);
        assertEquals(1, second.items().size());
        assertNull(second.nextCursor());

        assertNotEquals(first.items().getFirst().id, second.items().getFirst().id);
    }
}
