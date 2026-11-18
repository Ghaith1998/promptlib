package com.promptlib.adapter.prompt;

import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.shared.port.PromptLookup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class PromptLookupAdapter implements PromptLookup {

    private final PromptRepository prompts;

    @Inject
    public PromptLookupAdapter(PromptRepository prompts) {
        this.prompts = prompts;
    }

    @Override
    public boolean existsOwned(UUID promptId, UUID ownerId) {
        return prompts.findOwned(promptId, ownerId).isPresent();
    }

    @Override
    public boolean existsVisible(UUID promptId, UUID userId) {
        return prompts.findOwned(promptId, userId).isPresent()
                || prompts.findPublic(promptId).isPresent();
    }
}
