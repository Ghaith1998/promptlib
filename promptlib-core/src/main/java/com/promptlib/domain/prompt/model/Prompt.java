package com.promptlib.domain.prompt.model;

import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Aggregate root: identity and metadata. Content lives in {@link PromptVersion}. */
@Entity
@Table(name = "prompt")
public class Prompt {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "description")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    public Visibility visibility = Visibility.PRIVATE;

    @Column(name = "category_id")
    public UUID categoryId;

    @Column(name = "latest_version_id")
    public UUID latestVersionId;

    @Column(name = "published_version_id")
    public UUID publishedVersionId;

    @Column(name = "fork_of_prompt_id")
    public UUID forkOfPromptId;

    @Column(name = "fork_of_version_id")
    public UUID forkOfVersionId;

    @Column(name = "copy_count", nullable = false)
    public int copyCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_state", nullable = false)
    public ModerationState moderationState = ModerationState.VISIBLE;

    @Column(name = "hidden_reason")
    public String hiddenReason;

    @Column(name = "hidden_at")
    public Instant hiddenAt;

    @Column(name = "hidden_by")
    public UUID hiddenBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    public static Prompt create(UUID id, UUID ownerId, String title, String description, Instant now) {
        Prompt prompt = new Prompt();
        prompt.id = id;
        prompt.ownerId = ownerId;
        prompt.title = title == null ? null : title.trim();
        prompt.description = description;
        prompt.visibility = Visibility.PRIVATE;
        prompt.moderationState = ModerationState.VISIBLE;
        prompt.createdAt = now;
        prompt.updatedAt = now;
        return prompt;
    }

    public void updateMetadata(String title, String description, Instant now) {
        if (title != null) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description;
        }
        this.updatedAt = now;
    }

    /** Publishing pins exactly one version and requires an already-validated category. */
    public void publish(UUID versionId, UUID categoryId, Instant now) {
        if (moderationState == ModerationState.HIDDEN) {
            throw new Conflict(ProblemCode.HIDDEN_BY_ADMIN);
        }
        this.visibility = Visibility.PUBLIC;
        this.publishedVersionId = versionId;
        this.categoryId = categoryId;
        this.updatedAt = now;
    }

    public void unpublish(Instant now) {
        if (moderationState == ModerationState.HIDDEN) {
            throw new Conflict(ProblemCode.HIDDEN_BY_ADMIN);
        }
        this.visibility = Visibility.PRIVATE;
        this.publishedVersionId = null;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public void markForked(Instant now) {
        this.copyCount = this.copyCount + 1;
        this.updatedAt = now;
    }
}
