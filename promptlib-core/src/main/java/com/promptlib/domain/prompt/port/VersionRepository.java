package com.promptlib.domain.prompt.port;

import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository {

    int nextNumber(UUID promptId);

    Optional<PromptVersion> findByNumber(UUID promptId, int versionNumber);

    Optional<PromptVersion> byId(UUID versionId);

    List<PromptVersion> list(UUID promptId);

    void persist(PromptVersion version);

    void persistMessage(PromptMessage message);

    List<PromptMessage> messages(UUID versionId);
}
