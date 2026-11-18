package com.promptlib.domain.prompt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Ordered content of a version. Cascades on version delete. */
@Entity
@Table(name = "prompt_message")
public class PromptMessage {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "version_id", nullable = false)
    public UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    public MessageRole role;

    @Column(name = "position", nullable = false)
    public int position;

    @Column(name = "content", nullable = false)
    public String content;

    public static PromptMessage create(UUID id, UUID versionId, MessageRole role, int position, String content) {
        PromptMessage message = new PromptMessage();
        message.id = id;
        message.versionId = versionId;
        message.role = role;
        message.position = position;
        message.content = content;
        return message;
    }
}
