package com.promptlib.domain.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Per-user grouping. Filing is not ownership: deleting a collection never deletes prompts. */
@Entity
@Table(name = "collection")
public class Collection {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(name = "parent_id")
    public UUID parentId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "color")
    public String color;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(name = "is_default", nullable = false)
    public boolean isDefault;

    @Column(name = "default_marker")
    public String defaultMarker;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static Collection create(UUID id, UUID ownerId, String name, String description, String color,
                                    UUID parentId, int sortOrder, Instant now) {
        Collection collection = new Collection();
        collection.id = id;
        collection.ownerId = ownerId;
        collection.name = name;
        collection.description = description;
        collection.color = color;
        collection.parentId = parentId;
        collection.sortOrder = sortOrder;
        collection.isDefault = false;
        collection.createdAt = now;
        collection.updatedAt = now;
        return collection;
    }

    public static Collection createDefault(UUID id, UUID ownerId, Instant now) {
        Collection collection = create(id, ownerId, "My prompts", null, null, null, 0, now);
        collection.isDefault = true;
        collection.defaultMarker = "Y";
        return collection;
    }

    public void rename(String name, String description, String color, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (color != null) {
            this.color = color;
        }
        this.updatedAt = now;
    }
}
