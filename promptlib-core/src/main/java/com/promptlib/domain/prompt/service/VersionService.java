package com.promptlib.domain.prompt.service;

import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.model.VariableDefinition;
import com.promptlib.domain.prompt.model.VariableParser;
import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.domain.prompt.port.VersionRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Ids;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.error.ValidationFailed;
import com.promptlib.shared.port.CategoryLookup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Version lifecycle. Versions are write-once: editing always creates a new row,
 * and publishing only moves a pointer.
 */
@ApplicationScoped
public class VersionService {

    private final PromptRepository prompts;
    private final VersionRepository versions;
    private final CategoryLookup categories;
    private final Clock clock;

    @Inject
    public VersionService(PromptRepository prompts, VersionRepository versions,
                          CategoryLookup categories, Clock clock) {
        this.prompts = prompts;
        this.versions = versions;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public PromptVersion createVersion(UUID promptId, CallerContext caller, String changeNote,
                                       List<MessageDraft> messages, List<VariableDefinition> variables) {
        VariableParser.validate(messages, variables);

        Prompt prompt = prompts.lockOwned(promptId, caller.userId());
        Instant now = clock.instant();
        int nextNumber = versions.nextNumber(promptId);

        PromptVersion version = PromptVersion.create(
                Ids.newId(), prompt.id, nextNumber, caller.userId(), changeNote, variables, now);
        versions.persist(version);

        int position = 0;
        for (MessageDraft message : messages) {
            versions.persistMessage(
                    PromptMessage.create(Ids.newId(), version.id, message.role(), position++, message.content()));
        }

        prompt.latestVersionId = version.id;
        prompt.updatedAt = now;
        return version;
    }

    public List<PromptVersion> list(UUID promptId, CallerContext caller) {
        prompts.findOwned(promptId, caller.userId()).orElseThrow(NotFound::new);
        return versions.list(promptId);
    }

    public PromptVersion get(UUID promptId, int versionNumber, CallerContext caller) {
        prompts.findOwned(promptId, caller.userId()).orElseThrow(NotFound::new);
        return versions.findByNumber(promptId, versionNumber).orElseThrow(NotFound::new);
    }

    public List<PromptMessage> messages(UUID versionId, UUID promptId, CallerContext caller) {
        prompts.findOwned(promptId, caller.userId()).orElseThrow(NotFound::new);
        return versions.messages(versionId);
    }

    @Transactional
    public Prompt publish(UUID promptId, int versionNumber, UUID categoryId, CallerContext caller) {
        Prompt prompt = prompts.lockOwned(promptId, caller.userId());
        PromptVersion version = versions.findByNumber(promptId, versionNumber).orElseThrow(NotFound::new);
        categories.requireActive(categoryId);

        prompt.publish(version.id, categoryId, clock.instant());
        return prompt;
    }

    @Transactional
    public Prompt unpublish(UUID promptId, CallerContext caller) {
        Prompt prompt = prompts.lockOwned(promptId, caller.userId());
        prompt.unpublish(clock.instant());
        return prompt;
    }
}
