package com.promptlib.domain.account;

import com.promptlib.adapter.keycloak.KeycloakRegistrationAdapter;
import com.promptlib.domain.account.service.RegistrationService;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.ValidationFailed;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class RegistrationTest {

    @Inject
    RegistrationService registrations;
    @InjectMock
    KeycloakRegistrationAdapter keycloak;

    @Test
    void createsTheKeycloakAccountAndReturnsItsId() {
        UUID id = UUID.randomUUID();
        when(keycloak.createUser(any(), any(), any(), any())).thenReturn(id.toString());

        var registered = registrations.register("bob", "bob@example.com", "Bob", "longenough");

        assertEquals(id, registered.id());
        assertEquals("bob", registered.username());
    }

    @Test
    void weakPasswordIsRejectedBeforeCallingKeycloak() {
        ValidationFailed ex = assertThrows(ValidationFailed.class,
                () -> registrations.register("bob", "bob@example.com", null, "short"));
        assertEquals(ProblemCode.WEAK_PASSWORD, ex.code());
    }

    @Test
    void duplicateUsernameSurfacesAsConflict() {
        when(keycloak.createUser(any(), any(), any(), any()))
                .thenThrow(new Conflict(ProblemCode.USERNAME_TAKEN));

        Conflict ex = assertThrows(Conflict.class,
                () -> registrations.register("bob", "bob@example.com", null, "longenough"));
        assertEquals(ProblemCode.USERNAME_TAKEN, ex.code());
    }
}
