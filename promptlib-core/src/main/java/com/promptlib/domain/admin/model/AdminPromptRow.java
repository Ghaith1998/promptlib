package com.promptlib.domain.admin.model;

import java.time.Instant;
import java.util.UUID;

/** Metadata only. Deliberately carries no message content or variables. */
public record AdminPromptRow(UUID id, UUID ownerId, String ownerUsername, String title,
                             String visibility, String moderationState, int copyCount,
                             String hiddenReason, Instant createdAt) {
}
