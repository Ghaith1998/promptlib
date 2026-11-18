package com.promptlib.edge.api.dto.prompt;

public record PromptResponse(String id, String title, String description, String visibility,
                             String categoryId, String latestVersionId, String publishedVersionId,
                             String forkOfPromptId, int copyCount, String moderationState,
                             String hiddenReason, String createdAt, String updatedAt) {
}
