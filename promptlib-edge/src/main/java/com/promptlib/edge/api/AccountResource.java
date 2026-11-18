package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.account.MeResponse;
import com.promptlib.edge.api.dto.account.UpdateProfileRequest;
import com.promptlib.edge.client.AccountClient;
import com.promptlib.edge.mapping.DtoMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "keycloak")
public class AccountResource {

    @Inject
    AccountClient accounts;

    @GET
    public MeResponse me() {
        return DtoMapper.me(accounts.getMe());
    }

    @PATCH
    public MeResponse update(UpdateProfileRequest request) {
        return DtoMapper.me(accounts.updateProfile(request.displayName()));
    }
}
