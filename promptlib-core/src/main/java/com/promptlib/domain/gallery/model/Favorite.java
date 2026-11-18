package com.promptlib.domain.gallery.model;

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
@Table(name = "favorite")
@IdClass(Favorite.Key.class)
public class Favorite {

    @Id
    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Id
    @Column(name = "prompt_id", nullable = false)
    public UUID promptId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static class Key implements Serializable {

        public UUID userId;
        public UUID promptId;

        public Key() {
        }

        public Key(UUID userId, UUID promptId) {
            this.userId = userId;
            this.promptId = promptId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(promptId, k.promptId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, promptId);
        }
    }
}
