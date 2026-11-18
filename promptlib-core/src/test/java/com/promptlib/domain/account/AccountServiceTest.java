package com.promptlib.domain.account;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AccountServiceTest {

    @Inject
    AccountService accounts;

    @Test
    void firstCallCreatesTheMirrorAndLaterCallsUpdateIt() {
        String sub = UUID.randomUUID().toString();

        AppUser created = accounts.ensureUser(sub, "alice", "alice@example.com");
        assertNotNull(created.id);
        assertEquals("alice", created.username);

        AppUser again = accounts.ensureUser(sub, "alice-renamed", "new@example.com");
        assertEquals(created.id, again.id);
        assertEquals("alice-renamed", again.username);
        assertEquals("new@example.com", again.email);
    }

    @Test
    void displayNameIsApplicationOwned() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "bob", "bob@example.com");
        AppUser updated = accounts.updateDisplayName(user.id, "Bobby");

        assertEquals("Bobby", updated.displayName);
    }
}
