package com.promptlib.shared.port;

import java.util.UUID;

/** Minimal cross-domain checks; other domains must not import prompt classes. */
public interface PromptLookup {

    boolean existsOwned(UUID promptId, UUID ownerId);

    /** Owned by the caller or readable in the public gallery. */
    boolean existsVisible(UUID promptId, UUID userId);
}
