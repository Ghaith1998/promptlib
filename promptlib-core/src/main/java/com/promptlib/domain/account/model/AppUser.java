package com.promptlib.domain.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Local mirror of a Keycloak user. No credentials are ever stored here. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "keycloak_sub", nullable = false)
    public String keycloakSub;

    @Column(name = "username", nullable = false)
    public String username;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "display_name")
    public String displayName;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "last_seen_at")
    public Instant lastSeenAt;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    public void updateProfile(String displayName, Instant now) {
        this.displayName = displayName;
        this.updatedAt = now;
    }
}
