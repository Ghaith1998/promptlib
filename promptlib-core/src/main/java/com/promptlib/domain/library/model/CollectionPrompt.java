package com.promptlib.domain.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "collection_prompt")
@IdClass(CollectionPrompt.Key.class)
public class CollectionPrompt {

    @Id
    @Column(name = "collection_id", nullable = false)
    public UUID collectionId;

    @Id
    @Column(name = "prompt_id", nullable = false)
    public UUID promptId;

    @Column(name = "position", nullable = false)
    public int position;

    @Column(name = "added_at", nullable = false)
    public Instant addedAt;

    public static class Key implements Serializable {

        public UUID collectionId;
        public UUID promptId;

        public Key() {
        }

        public Key(UUID collectionId, UUID promptId) {
            this.collectionId = collectionId;
            this.promptId = promptId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k
                    && Objects.equals(collectionId, k.collectionId)
                    && Objects.equals(promptId, k.promptId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collectionId, promptId);
        }
    }
}
