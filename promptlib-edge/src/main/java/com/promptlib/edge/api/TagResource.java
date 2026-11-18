package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.library.TagResponse;
import com.promptlib.edge.client.LibraryClient;
import com.promptlib.edge.mapping.DtoMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

@Path("/api/v1/tags")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "keycloak")
public class TagResource {

    @Inject
    LibraryClient library;

    @GET
    public List<TagResponse> list() {
        return library.listTags().getItemsList().stream().map(DtoMapper::tag).toList();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        library.deleteTag(id);
        return Response.noContent().build();
    }
}
