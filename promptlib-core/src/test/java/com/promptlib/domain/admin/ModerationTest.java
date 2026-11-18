package com.promptlib.domain.admin;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.admin.port.AuditRepository;
import com.promptlib.domain.admin.service.ModerationService;
import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.domain.gallery.service.GalleryService;
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
class ModerationTest {

    @Inject AccountService accounts;
    @Inject CategoryService categories;
    @Inject PromptService prompts;
    @Inject VersionService versions;
    @Inject GalleryService gallery;
    @Inject ModerationService moderation;
    @Inject AuditRepository audit;

    private CallerContext caller(String role) {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "m-" + UUID.randomUUID(), "m@example.com");
        return new CallerContext(user.id, user.keycloakSub, Set.of(role));
    }

    private Prompt published(CallerContext owner) {
        Prompt prompt = prompts.create(owner, new PromptDraft("Moderated", null,
                List.of(new MessageDraft(MessageRole.USER, "text")), List.of()));
        versions.publish(prompt.id, 1, categories.listActive().getFirst().id, owner);
        return prompt;
    }

    @Test
    void hidingRemovesFromGalleryAndIsAudited() {
        CallerContext owner = caller("user");
        CallerContext admin = caller("admin");
        Prompt prompt = published(owner);

        moderation.hide(admin, prompt.id, "off topic", "req-1");

        assertTrue(gallery.browse(null, null, 100).items().stream().noneMatch(i -> i.id().equals(prompt.id)));
        assertEquals(1, audit.listForTarget("PROMPT", prompt.id).size());

        moderation.unhide(admin, prompt.id, "req-2");

        assertTrue(gallery.browse(null, null, 100).items().stream().anyMatch(i -> i.id().equals(prompt.id)));
        assertEquals(2, audit.listForTarget("PROMPT", prompt.id).size());
    }

    @Test
    void nonAdminsCannotModerate() {
        CallerContext owner = caller("user");
        Prompt prompt = published(owner);

        assertThrows(Forbidden.class, () -> moderation.hide(owner, prompt.id, "because", "req"));
    }
}
