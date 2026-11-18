package com.promptlib.edge.api.dto.admin;

/** Metadata only: the admin surface never returns prompt content. */
public record AdminPromptItemResponse(String id, String ownerId, String ownerUsername, String title,
                                      String visibility, String moderationState, int copyCount,
                                      String hiddenReason, String createdAt) {
}
