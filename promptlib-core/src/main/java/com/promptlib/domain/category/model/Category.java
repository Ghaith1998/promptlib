package com.promptlib.domain.category.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Global taxonomy value. Admin-managed; required when a prompt is published. */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "slug", nullable = false)
    public String slug;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "color")
    public String color;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(name = "active", nullable = false)
    public boolean active;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
