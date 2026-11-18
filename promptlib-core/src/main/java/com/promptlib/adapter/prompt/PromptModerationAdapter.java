package com.promptlib.adapter.prompt;

import com.promptlib.domain.prompt.model.ModerationState;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.port.PromptModeration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class PromptModerationAdapter implements PromptModeration {

    private final PromptRepository prompts;
    private final Clock clock;

    @Inject
    public PromptModerationAdapter(PromptRepository prompts, Clock clock) {
        this.prompts = prompts;
        this.clock = clock;
    }

    @Override
    public void hide(UUID promptId, String reason, UUID adminId) {
        Prompt prompt = prompts.byId(promptId).orElseThrow(NotFound::new);
        Instant now = clock.instant();
        prompt.moderationState = ModerationState.HIDDEN;
        prompt.hiddenReason = reason;
        prompt.hiddenAt = now;
        prompt.hiddenBy = adminId;
        prompt.updatedAt = now;
    }

    @Override
    public void unhide(UUID promptId) {
        Prompt prompt = prompts.byId(promptId).orElseThrow(NotFound::new);
        prompt.moderationState = ModerationState.VISIBLE;
        prompt.hiddenReason = null;
        prompt.hiddenAt = null;
        prompt.hiddenBy = null;
        prompt.updatedAt = clock.instant();
    }
}
