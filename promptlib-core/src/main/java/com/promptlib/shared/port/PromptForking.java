package com.promptlib.shared.port;

import com.promptlib.shared.CallerContext;

import java.util.UUID;

/** Copying a published prompt into another workspace. Implemented over the prompt aggregate. */
public interface PromptForking {

    /** @return the id of the new, private prompt */
    UUID fork(UUID sourcePromptId, CallerContext caller);
}
