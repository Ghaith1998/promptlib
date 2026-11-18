package com.promptlib.domain.prompt.service;

import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.model.VariableParser;
import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.domain.prompt.port.VersionRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Cursor;
import com.promptlib.shared.Ids;
import com.promptlib.shared.Page;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.ValidationFailed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PromptService {

    private final PromptRepository prompts;
    private final VersionRepository versions;
    private final Clock clock;

    @Inject
    public PromptService(PromptRepository prompts, VersionRepository versions, Clock clock) {
        this.prompts = prompts;
        this.versions = versions;
        this.clock = clock;
    }

    /** Creates the prompt and its first version in one transaction. */
    @Transactional
    public Prompt create(CallerContext caller, PromptDraft draft) {
        if (draft.title() == null || draft.title().isBlank()) {
            throw new ValidationFailed(ProblemCode.EMPTY_PROMPT);
        }
        VariableParser.validate(draft.messages(), draft.variables());

        Instant now = clock.instant();
        Prompt prompt = Prompt.create(Ids.newId(), caller.userId(), draft.title(), draft.description(), now);
        prompts.persist(prompt);

        PromptVersion version = PromptVersion.create(
                Ids.newId(), prompt.id, 1, caller.userId(), null, draft.variables(), now);
        versions.persist(version);

        int position = 0;
        for (MessageDraft message : draft.messages()) {
            versions.persistMessage(
                    PromptMessage.create(Ids.newId(), version.id, message.role(), position++, message.content()));
        }

        prompt.latestVersionId = version.id;
        prompt.updatedAt = now;
        return prompt;
    }

    public Prompt getOwned(CallerContext caller, UUID promptId) {
        return prompts.findOwned(promptId, caller.userId()).orElseThrow(com.promptlib.shared.error.NotFound::new);
    }

    public Optional<Prompt> findVisible(UUID promptId, UUID ownerId) {
        return prompts.findOwned(promptId, ownerId);
    }

    public Page<Prompt> listOwned(CallerContext caller, String cursor, String query, int limit) {
        int size = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        Cursor decoded = cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor);

        List<Prompt> rows = prompts.pageOwned(caller.userId(), decoded, query, size + 1);
        boolean hasMore = rows.size() > size;
        List<Prompt> items = hasMore ? rows.subList(0, size) : rows;
        String next = null;
        if (hasMore) {
            Prompt last = items.get(items.size() - 1);
            next = Cursor.of(last.createdAt, last.id).encode();
        }
        return Page.of(items, next);
    }

    @Transactional
    public Prompt updateMetadata(CallerContext caller, UUID promptId, String title, String description) {
        Prompt prompt = getOwned(caller, promptId);
        if (title != null && title.isBlank()) {
            throw new ValidationFailed(ProblemCode.EMPTY_PROMPT);
        }
        prompt.updateMetadata(title, description, clock.instant());
        return prompt;
    }

    @Transactional
    public void delete(CallerContext caller, UUID promptId) {
        Prompt prompt = prompts.lockOwned(promptId, caller.userId());
        if (prompt.visibility == com.promptlib.domain.prompt.model.Visibility.PUBLIC) {
            throw new Conflict(ProblemCode.PROMPT_PUBLISHED);
        }
        if (prompt.deletedAt != null) {
            return;
        }
        prompt.softDelete(clock.instant());
        prompts.deleteDependentRows(prompt.id);
    }
}
