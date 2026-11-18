package com.promptlib.adapter.keycloak;

import java.util.List;

/** Request body of POST /admin/realms/{realm}/users. */
public record CreateKeycloakUser(String username, String email, boolean enabled, boolean emailVerified,
                                 List<Credential> credentials) {

    public record Credential(String type, String value, boolean temporary) {
    }
}
