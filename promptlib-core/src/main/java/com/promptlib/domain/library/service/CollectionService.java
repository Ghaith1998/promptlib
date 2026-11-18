package com.promptlib.domain.library.service;

import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.model.CollectionPrompt;
import com.promptlib.domain.library.port.CollectionRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Ids;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.validation.Colour;
import com.promptlib.shared.port.PromptLookup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollectionService {

    private final CollectionRepository collections;
    private final PromptLookup prompts;
    private final Clock clock;

    @Inject
    public CollectionService(CollectionRepository collections, PromptLookup prompts, Clock clock) {
        this.collections = collections;
        this.prompts = prompts;
        this.clock = clock;
    }

    /** One default collection per user; concurrent first calls are settled by the unique key. */
    @Transactional
    public Collection ensureDefault(CallerContext caller) {
        var existing = collections.findDefault(caller.userId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Collection created = Collection.createDefault(Ids.newId(), caller.userId(), clock.instant());
        try {
            collections.persist(created);
            collections.flush();
            return created;
        } catch (PersistenceException raced) {
            return collections.findDefault(caller.userId()).orElseThrow(() -> new IllegalStateException("default collection vanished after concurrent insert", raced));
        }
    }

    public List<Collection> list(CallerContext caller) {
        ensureDefault(caller);
        return collections.listOwned(caller.userId());
    }

    @Transactional
    public Collection create(CallerContext caller, String name, String description, String color, UUID parentId) {
        if (name == null || name.isBlank()) {
            throw new com.promptlib.shared.error.ValidationFailed(ProblemCode.VALIDATION_FAILED);
        }
        Instant now = clock.instant();
        Collection collection = Collection.create(Ids.newId(), caller.userId(), name.trim(), description,
                Colour.normalise(color), parentId, 0, now);
        collections.persist(collection);
        return collection;
    }

    @Transactional
    public Collection rename(CallerContext caller, UUID collectionId, String name, String description, String color) {
        Collection collection = requireOwned(caller, collectionId);
        if (name != null && name.isBlank()) {
            throw new com.promptlib.shared.error.ValidationFailed(ProblemCode.VALIDATION_FAILED);
        }
        collection.rename(name, description, Colour.normalise(color), clock.instant());
        return collection;
    }

    @Transactional
    public void delete(CallerContext caller, UUID collectionId) {
        Collection collection = requireOwned(caller, collectionId);
        if (collection.isDefault) {
            throw new Conflict(ProblemCode.DEFAULT_COLLECTION);
        }
        if (collections.hasChildren(collectionId) || collections.countPrompts(collectionId) > 0) {
            throw new Conflict(ProblemCode.COLLECTION_NOT_EMPTY);
        }
        collections.delete(collection);
    }

    @Transactional
    public void addPrompt(CallerContext caller, UUID collectionId, UUID promptId) {
        Collection collection = requireOwned(caller, collectionId);
        if (!prompts.existsOwned(promptId, caller.userId())) {
            throw new NotFound();
        }
        if (collections.hasPrompt(collection.id, promptId)) {
            return;
        }
        CollectionPrompt membership = new CollectionPrompt();
        membership.collectionId = collection.id;
        membership.promptId = promptId;
        membership.position = (int) collections.countPrompts(collection.id);
        membership.addedAt = clock.instant();
        collections.addPrompt(membership);
    }

    public Collection requireOwned(CallerContext caller, UUID collectionId) {
        return collections.findOwned(collectionId, caller.userId()).orElseThrow(NotFound::new);
    }
}
