package com.promptlib.shared;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, resolved once at the edge and propagated as gRPC metadata.
 * Identity never comes from a request body.
 */
public record CallerContext(UUID userId, String keycloakSub, Set<String> roles) {

    public CallerContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains("admin");
    }
}
