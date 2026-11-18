package com.promptlib.edge.security;

import com.promptlib.edge.client.AccountClient;
import com.promptlib.proto.v1.UserReply;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Priority;
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
    JsonWebToken jwt;
    @Inject
    AccountClient accounts;
    @Inject
    RequestIdentity identity;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/auth/register")) {
            return;
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        String username = claim("preferred_username", sub);
        String email = claim("email", "");
        Set<String> roles = jwt.getGroups() == null ? Set.of() : jwt.getGroups();

        try {
            UserReply me = accounts.ensureUser(sub, username, email, roles);
            identity.resolve(UUID.fromString(me.getId()), sub, roles);
        } catch (StatusRuntimeException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE).build());
        }
    }

    private String claim(String name, String fallback) {
        Object value = jwt.getClaim(name);
        return value == null ? fallback : value.toString();
    }
}
