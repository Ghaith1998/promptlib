package com.promptlib.domain.account.service;

import com.promptlib.domain.account.port.KeycloakRegistrationPort;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Account creation. No app_user row is written here: the mirror is created on first login,
 * so a failure between Keycloak and the database cannot leave a half-created account.
 */
@ApplicationScoped
public class RegistrationService {

    private static final int MIN_PASSWORD = 8;

    private final KeycloakRegistrationPort keycloak;

    @Inject
    public RegistrationService(KeycloakRegistrationPort keycloak) {
        this.keycloak = keycloak;
    }

    public RegisteredUser register(String username, String email, String displayName, String password) {
        if (username == null || username.isBlank() || username.length() > 64) {
            throw new ValidationFailed(ProblemCode.VALIDATION_FAILED, "invalid username");
        }
        if (email == null || !email.contains("@") || email.length() > 255) {
            throw new ValidationFailed(ProblemCode.VALIDATION_FAILED, "invalid email");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new ValidationFailed(ProblemCode.WEAK_PASSWORD);
        }
        String id = keycloak.createUser(username.trim(), email.trim(), displayName, password);
        return new RegisteredUser(UUID.fromString(id), username.trim(), email.trim());
    }

    public record RegisteredUser(UUID id, String username, String email) {
    }
}
