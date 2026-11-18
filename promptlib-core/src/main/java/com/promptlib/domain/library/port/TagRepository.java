package com.promptlib.domain.library.port;

import com.promptlib.domain.library.model.Tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository {

    Optional<Tag> findOwned(UUID id, UUID ownerId);

    Optional<Tag> findByName(UUID ownerId, String name);

    List<Tag> listOwned(UUID ownerId);

    long countUsage(UUID tagId);

    void persist(Tag tag);

    void delete(Tag tag);

    void attach(UUID promptId, UUID tagId);

    void detachAll(UUID promptId);

    List<Tag> tagsOf(UUID promptId);
}
