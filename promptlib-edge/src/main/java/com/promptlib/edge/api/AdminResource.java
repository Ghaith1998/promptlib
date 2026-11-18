package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.admin.AdminPromptItemResponse;
import com.promptlib.edge.api.dto.admin.HideRequest;
import com.promptlib.edge.client.AdminClient;
import com.promptlib.edge.mapping.DtoMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/v1/admin/prompts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@SecurityRequirement(name = "keycloak")
public class AdminResource {

    @Inject
    AdminClient admin;

    @GET
    public List<AdminPromptItemResponse> list() {
        return admin.listPrompts().getItemsList().stream().map(DtoMapper::adminPrompt).toList();
    }

    @POST
    @Path("/{id}/hide")
    public Response hide(@PathParam("id") String id, HideRequest request) {
        admin.hide(id, request.reason());
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/unhide")
    public Response unhide(@PathParam("id") String id) {
        admin.unhide(id);
        return Response.ok().build();
    }
}
