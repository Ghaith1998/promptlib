package com.promptlib.domain.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "prompt_tag")
@IdClass(PromptTag.Key.class)
public class PromptTag {

    @Id
    @Column(name = "prompt_id", nullable = false)
    public UUID promptId;

    @Id
    @Column(name = "tag_id", nullable = false)
    public UUID tagId;

    public static class Key implements Serializable {

        public UUID promptId;
        public UUID tagId;

        public Key() {
        }

        public Key(UUID promptId, UUID tagId) {
            this.promptId = promptId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(promptId, k.promptId) && Objects.equals(tagId, k.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(promptId, tagId);
        }
    }
}
