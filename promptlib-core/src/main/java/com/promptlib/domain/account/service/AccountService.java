package com.promptlib.domain.account.service;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.port.AppUserRepository;
import com.promptlib.shared.Ids;
import com.promptlib.shared.error.NotFound;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Account acquisition and profile. The Keycloak token is the source of truth for
 * username and email; display name belongs to the application.
 */
@ApplicationScoped
@Transactional
public class AccountService {

    private final AppUserRepository users;
    private final Clock clock;

    @Inject
    public AccountService(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /**
     * Idempotent mirror of the authenticated user. Concurrent first requests are safe:
     * the unique key on keycloak_sub decides, and the loser re-reads the winner's row.
     */
    @Transactional
    public AppUser ensureUser(String sub, String username, String email) {
        Instant now = clock.instant();
        var existing = users.findByKeycloakSub(sub);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            user.username = username;
            user.email = email;
            user.lastSeenAt = now;
            user.updatedAt = now;
            return user;
        }

        AppUser created = new AppUser();
        created.id = Ids.newId();
        created.keycloakSub = sub;
        created.username = username;
        created.email = email;
        created.createdAt = now;
        created.updatedAt = now;
        created.lastSeenAt = now;
        try {
            users.persist(created);
            users.flush();
            return created;
        } catch (PersistenceException raced) {
            return users.findByKeycloakSub(sub).orElseThrow(() -> new IllegalStateException("user vanished after concurrent insert", raced));
        }
    }

    public AppUser require(UUID userId) {
        return users.byId(userId).orElseThrow(NotFound::new);
    }

    @Transactional
    public AppUser updateDisplayName(UUID userId, String displayName) {
        AppUser user = require(userId);
        user.updateProfile(displayName, clock.instant());
        return user;
    }
}
