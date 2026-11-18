package com.promptlib.domain.prompt.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable content snapshot. There is no setter for content: editing creates a new row. */
@Entity
@Table(name = "prompt_version")
public class PromptVersion {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "prompt_id", nullable = false)
    public UUID promptId;

    @Column(name = "version_number", nullable = false)
    public int versionNumber;

    @Column(name = "change_note")
    public String changeNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_json", nullable = false)
    public List<VariableDefinition> variables = new ArrayList<>();

    @Column(name = "created_by", nullable = false)
    public UUID createdBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    public static PromptVersion create(UUID id, UUID promptId, int versionNumber, UUID createdBy,
                                       String changeNote, List<VariableDefinition> variables,
                                       Instant now) {
        PromptVersion version = new PromptVersion();
        version.id = id;
        version.promptId = promptId;
        version.versionNumber = versionNumber;
        version.createdBy = createdBy;
        version.changeNote = changeNote;
        version.variables = new ArrayList<>(variables);
        version.createdAt = now;
        return version;
    }
}
