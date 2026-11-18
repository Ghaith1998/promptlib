package com.promptlib.domain.library.port;

import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.model.CollectionPrompt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository {

    Optional<Collection> findDefault(UUID ownerId);

    Optional<Collection> findOwned(UUID id, UUID ownerId);

    List<Collection> listOwned(UUID ownerId);

    boolean hasChildren(UUID collectionId);

    long countPrompts(UUID collectionId);

    void persist(Collection collection);

    void delete(Collection collection);

    void addPrompt(CollectionPrompt membership);

    boolean hasPrompt(UUID collectionId, UUID promptId);

    java.util.List<UUID> promptIds(UUID collectionId);

    void flush();
}
