package com.promptlib.domain.prompt.port;

import com.promptlib.domain.prompt.model.Prompt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptRepository {

    Optional<Prompt> findOwned(UUID id, UUID ownerId);

    Optional<Prompt> byId(UUID id);

    /** Public, visible, not deleted. The only shape the gallery may read. */
    Optional<Prompt> findPublic(UUID id);

    /** Row lock used by version creation and publishing. */
    Prompt lockOwned(UUID id, UUID ownerId);

    List<Prompt> pageOwned(UUID ownerId, com.promptlib.shared.Cursor cursor, int limit);

    void persist(Prompt prompt);

    /** Atomic counter bump; no read-modify-write race. */
    void incrementCopyCount(UUID promptId, java.time.Instant now);

    /** Reloads a managed entity after a bulk update bypassed the persistence context. */
    void refresh(Prompt prompt);

    /** Removes memberships/favourites/search rows when a prompt is soft-deleted. */
    void deleteDependentRows(UUID promptId);
}
