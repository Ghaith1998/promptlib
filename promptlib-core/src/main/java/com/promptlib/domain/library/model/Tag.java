package com.promptlib.domain.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "color")
    public String color;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static Tag create(UUID id, UUID ownerId, String name, String color, Instant now) {
        Tag tag = new Tag();
        tag.id = id;
        tag.ownerId = ownerId;
        tag.name = name;
        tag.color = color;
        tag.createdAt = now;
        return tag;
    }
}
