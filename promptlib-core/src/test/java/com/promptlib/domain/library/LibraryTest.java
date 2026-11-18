package com.promptlib.domain.library;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.model.Tag;
import com.promptlib.domain.library.port.CollectionRepository;
import com.promptlib.domain.library.service.CollectionService;
import com.promptlib.domain.library.service.TagService;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.MessageRole;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.service.PromptService;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LibraryTest {

    @Inject AccountService accounts;
    @Inject CollectionService collections;
    @Inject TagService tags;
    @Inject PromptService prompts;
    @Inject CollectionRepository collectionRepository;

    private CallerContext newCaller() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "u-" + UUID.randomUUID(), "u@example.com");
        return new CallerContext(user.id, user.keycloakSub, Set.of("user"));
    }

    private Prompt newPrompt(CallerContext caller) {
        return prompts.create(caller, new PromptDraft("p", null,
                List.of(new MessageDraft(MessageRole.USER, "hello")), List.of()));
    }

    @Test
    void defaultCollectionIsCreatedOncePerUser() {
        CallerContext caller = newCaller();
        Collection first = collections.ensureDefault(caller);
        Collection second = collections.ensureDefault(caller);

        assertEquals(first.id, second.id);
        assertTrue(first.isDefault);
        assertEquals("My prompts", first.name);
    }

    @Test
    void defaultCollectionCannotBeDeleted() {
        CallerContext caller = newCaller();
        Collection def = collections.ensureDefault(caller);

        Conflict ex = assertThrows(Conflict.class, () -> collections.delete(caller, def.id));
        assertEquals(ProblemCode.DEFAULT_COLLECTION, ex.code());
    }

    @Test
    void nonEmptyCollectionCannotBeDeleted() {
        CallerContext caller = newCaller();
        Collection collection = collections.create(caller, "Code", null, null, null);
        Prompt prompt = newPrompt(caller);
        collections.addPrompt(caller, collection.id, prompt.id);

        Conflict ex = assertThrows(Conflict.class, () -> collections.delete(caller, collection.id));
        assertEquals(ProblemCode.COLLECTION_NOT_EMPTY, ex.code());
    }

    @Test
    void emptyCollectionCanBeDeleted() {
        CallerContext caller = newCaller();
        Collection collection = collections.create(caller, "Temp", null, "#4f46e5", null);

        collections.delete(caller, collection.id);

        assertTrue(collections.list(caller).stream().noneMatch(c -> c.id.equals(collection.id)));
    }

    @Test
    void tagsAreCreatedOnDemandAndCannotBeDeletedWhileAttached() {
        CallerContext caller = newCaller();
        Prompt prompt = newPrompt(caller);

        List<Tag> attached = tags.setForPrompt(caller, prompt.id, List.of("review", "java"));
        assertEquals(2, attached.size());

        Tag review = attached.getFirst();
        Conflict ex = assertThrows(Conflict.class, () -> tags.delete(caller, review.id));
        assertEquals(ProblemCode.TAG_IN_USE, ex.code());

        tags.setForPrompt(caller, prompt.id, List.of());
        tags.delete(caller, review.id);
        assertTrue(tags.list(caller).stream().noneMatch(t -> t.id.equals(review.id)));
    }

    @Test
    void addingAPromptToACollectionIsIdempotent() {
        CallerContext caller = newCaller();
        Collection collection = collections.create(caller, "Once", null, null, null);
        Prompt prompt = newPrompt(caller);

        collections.addPrompt(caller, collection.id, prompt.id);
        collections.addPrompt(caller, collection.id, prompt.id);

        assertEquals(1, collectionRepository.countPrompts(collection.id));
    }
}
