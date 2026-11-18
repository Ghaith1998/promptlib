package com.promptlib.adapter.keycloak;

import java.util.List;

/**
 * Request body of POST /admin/realms/{realm}/users.
 * firstName/lastName are required by the realm's user profile; without them Keycloak
 * creates the account but refuses to log it in ("Account is not fully set up").
 */
public record CreateKeycloakUser(String username, String email, String firstName, String lastName,
                                 boolean enabled, boolean emailVerified, List<Credential> credentials) {

    public record Credential(String type, String value, boolean temporary) {
    }
}
