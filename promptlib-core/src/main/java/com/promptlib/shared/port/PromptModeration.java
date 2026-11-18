package com.promptlib.shared.port;

import java.util.UUID;

/** Admin moderation, applied to the prompt aggregate. */
public interface PromptModeration {

    void hide(UUID promptId, String reason, UUID adminId);

    void unhide(UUID promptId);
}
