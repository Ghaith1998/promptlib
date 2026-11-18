package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.library.AddPromptRequest;
import com.promptlib.edge.api.dto.library.CollectionResponse;
import com.promptlib.edge.api.dto.library.CollectionUpdateRequest;
import com.promptlib.edge.api.dto.library.CreateCollectionRequest;
import com.promptlib.edge.client.LibraryClient;
import com.promptlib.edge.mapping.DtoMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.net.URI;
import java.util.List;

@Path("/api/v1/collections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@SecurityRequirement(name = "keycloak")
public class CollectionResource {

    @Inject
    LibraryClient library;

    @GET
    public List<CollectionResponse> list() {
        return library.listCollections().getItemsList().stream().map(DtoMapper::collection).toList();
    }

    @POST
    public Response create(CreateCollectionRequest request) {
        var created = library.createCollection(request.name(), request.color());
        return Response.created(URI.create("/api/v1/collections/" + created.getId()))
                .entity(DtoMapper.collection(created))
                .build();
    }

    @PATCH
    @Path("/{id}")
    public CollectionResponse update(@PathParam("id") String id, CollectionUpdateRequest request) {
        return DtoMapper.collection(library.updateCollection(id, request.name(), request.description(),
                request.color(), request.sortOrder()));
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        library.deleteCollection(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/prompts")
    public Response addPrompt(@PathParam("id") String id, AddPromptRequest request) {
        library.addPrompt(id, request.promptId());
        return Response.noContent().build();
    }
}
