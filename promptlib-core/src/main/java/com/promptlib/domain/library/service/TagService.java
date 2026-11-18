package com.promptlib.domain.library.service;

import com.promptlib.domain.library.model.Tag;
import com.promptlib.domain.library.port.TagRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Ids;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.validation.Colour;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TagService {

    private final TagRepository tags;
    private final Clock clock;

    @Inject
    public TagService(TagRepository tags, Clock clock) {
        this.tags = tags;
        this.clock = clock;
    }

    public List<Tag> list(CallerContext caller) {
        return tags.listOwned(caller.userId());
    }

    /** Attaches the given names, creating missing tags, and detaches the rest. */
    @Transactional
    public List<Tag> setForPrompt(CallerContext caller, UUID promptId, List<String> names) {
        tags.detachAll(promptId);
        List<Tag> attached = new ArrayList<>();
        for (String raw : names == null ? List.<String>of() : names) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            Tag tag = tags.findByName(caller.userId(), name)
                    .orElseGet(() -> {
                        Tag created = Tag.create(Ids.newId(), caller.userId(), name, null, clock.instant());
                        tags.persist(created);
                        return created;
                    });
            tags.attach(promptId, tag.id);
            attached.add(tag);
        }
        return attached;
    }

    public List<Tag> tagsOf(UUID promptId) {
        return tags.tagsOf(promptId);
    }

    @Transactional
    public Tag rename(CallerContext caller, UUID tagId, String name, String color) {
        Tag tag = tags.findOwned(tagId, caller.userId()).orElseThrow(NotFound::new);
        if (name != null && !name.isBlank()) {
            tag.name = name.trim();
        }
        tag.color = Colour.normalise(color);
        return tag;
    }

    @Transactional
    public void delete(CallerContext caller, UUID tagId) {
        Tag tag = tags.findOwned(tagId, caller.userId()).orElseThrow(NotFound::new);
        if (tags.countUsage(tag.id) > 0) {
            throw new Conflict(ProblemCode.TAG_IN_USE);
        }
        tags.delete(tag);
    }
}
