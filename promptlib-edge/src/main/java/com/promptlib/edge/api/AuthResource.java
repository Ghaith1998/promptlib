package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.account.RegisterRequest;
import com.promptlib.edge.api.dto.account.RegisterResponse;
import com.promptlib.edge.client.AccountClient;
import com.promptlib.proto.v1.UserReply;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

/** The only unauthenticated endpoint. Creates the Keycloak account, nothing else. */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AccountClient accounts;

    @POST
    @Path("/register")
    public Response register(RegisterRequest request) {
        UserReply created = accounts.register(request.username(), request.email(),
                request.displayName(), request.password());
        return Response.created(URI.create("/api/v1/me"))
                .entity(new RegisterResponse(created.getId(), created.getUsername(), created.getEmail()))
                .build();
    }
}
