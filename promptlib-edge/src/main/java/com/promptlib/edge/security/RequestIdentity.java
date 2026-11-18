package com.promptlib.edge.security;

import jakarta.enterprise.context.RequestScoped;

import java.util.Set;
import java.util.UUID;

/** Per-request caller identity, resolved once by the filter and reused by every client call. */
@RequestScoped
public class RequestIdentity {

    private UUID userId;
    private String sub;
    private Set<String> roles = Set.of();
    private String requestId;

    public UUID userId() {
        return userId;
    }

    public String sub() {
        return sub;
    }

    public Set<String> roles() {
        return roles;
    }

    public String requestId() {
        return requestId;
    }

    public void resolve(UUID userId, String sub, Set<String> roles) {
        this.userId = userId;
        this.sub = sub;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public void requestId(String requestId) {
        this.requestId = requestId;
    }
}
