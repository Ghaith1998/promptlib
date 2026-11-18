package com.promptlib.edge.security;

import com.promptlib.edge.client.AccountClient;
import com.promptlib.proto.v1.UserReply;
import io.grpc.StatusRuntimeException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;
import java.util.UUID;

/**
 * The edge is the only place that talks to Keycloak. It mirrors the user and stashes the
 * application id, so every later gRPC call can carry x-user-id.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class IdentityFilter implements ContainerRequestFilter {

    @Inject
    Instance<JsonWebToken> jwt;
    @Inject
    SecurityIdentity securityIdentity;
    @Inject
    AccountClient accounts;
    @Inject
    RequestIdentity identity;

    @Override
    public void filter(ContainerRequestContext request) {
        if (request.getUriInfo().getPath().contains("auth/register")) {
            return;
        }
        if (securityIdentity.isAnonymous()) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        JsonWebToken token = jwt.isResolvable() ? jwt.get() : null;
        String sub = token != null ? token.getSubject() : securityIdentity.getPrincipal().getName();
        if (sub == null || sub.isBlank()) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        String username = token != null ? claim(token, "preferred_username", sub) : sub;
        String email = token != null ? claim(token, "email", "") : "";
        // Quarkus maps realm_access.roles into the SecurityIdentity; getGroups() would
        // only see a "groups" claim that this realm does not emit.
        Set<String> roles = securityIdentity.getRoles();

        try {
            UserReply me = accounts.ensureUser(sub, username, email, roles);
            identity.resolve(UUID.fromString(me.getId()), sub, roles);
        } catch (StatusRuntimeException e) {
            throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    private String claim(JsonWebToken token, String name, String fallback) {
        Object value = token.getClaim(name);
        return value == null ? fallback : value.toString();
    }
}
