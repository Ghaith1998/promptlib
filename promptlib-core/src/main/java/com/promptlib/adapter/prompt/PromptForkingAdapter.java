package com.promptlib.adapter.prompt;

import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.port.CollectionRepository;
import com.promptlib.domain.library.service.CollectionService;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.domain.prompt.port.VersionRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Ids;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.port.PromptForking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A fork is a new prompt owned outright by the forker: content is copied, not referenced.
 * The copied version records the forker as its author; credit lives in fork_of_*.
 */
@ApplicationScoped
public class PromptForkingAdapter implements PromptForking {

    private final PromptRepository prompts;
    private final VersionRepository versions;
    private final CollectionRepository collections;
    private final CollectionService collectionsService;
    private final Clock clock;

    @Inject
    public PromptForkingAdapter(PromptRepository prompts, VersionRepository versions,
                                CollectionRepository collections, CollectionService collectionsService,
                                Clock clock) {
        this.prompts = prompts;
        this.versions = versions;
        this.collections = collections;
        this.collectionsService = collectionsService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID fork(UUID sourcePromptId, CallerContext caller) {
        Prompt source = prompts.findPublic(sourcePromptId).orElseThrow(NotFound::new);
        if (source.publishedVersionId == null) {
            throw new NotFound();
        }
        PromptVersion published = versions.byId(source.publishedVersionId).orElseThrow(NotFound::new);
        List<PromptMessage> sourceMessages = versions.messages(published.id);

        Instant now = clock.instant();

        Prompt copy = Prompt.create(Ids.newId(), caller.userId(),
                source.title + " (fork)", source.description, now);
        copy.forkOfPromptId = source.id;
        copy.forkOfVersionId = published.id;
        prompts.persist(copy);

        PromptVersion version = PromptVersion.create(Ids.newId(), copy.id, 1, caller.userId(),
                null, published.variables, now);
        versions.persist(version);

        int position = 0;
        for (PromptMessage message : sourceMessages) {
            versions.persistMessage(PromptMessage.create(
                    Ids.newId(), version.id, message.role, position++, message.content));
        }

        copy.latestVersionId = version.id;
        prompts.incrementCopyCount(source.id, now);
        prompts.refresh(source);

        Collection defaultCollection = collectionsService.ensureDefault(caller);
        if (!collections.hasPrompt(defaultCollection.id, copy.id)) {
            com.promptlib.domain.library.model.CollectionPrompt membership =
                    new com.promptlib.domain.library.model.CollectionPrompt();
            membership.collectionId = defaultCollection.id;
            membership.promptId = copy.id;
            membership.position = (int) collections.countPrompts(defaultCollection.id);
            membership.addedAt = now;
            collections.addPrompt(membership);
        }

        return copy.id;
    }
}
