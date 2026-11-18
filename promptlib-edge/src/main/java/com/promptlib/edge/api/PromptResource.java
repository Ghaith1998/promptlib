package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.common.PageResponse;
import com.promptlib.edge.api.dto.library.SetTagsRequest;
import com.promptlib.edge.api.dto.library.TagResponse;
import com.promptlib.edge.api.dto.prompt.CreatePromptRequest;
import com.promptlib.edge.api.dto.prompt.PromptResponse;
import com.promptlib.edge.api.dto.prompt.UpdatePromptRequest;
import com.promptlib.edge.client.GalleryClient;
import com.promptlib.edge.client.LibraryClient;
import com.promptlib.edge.client.PromptClient;
import com.promptlib.edge.mapping.DtoMapper;
import com.promptlib.proto.v1.ListPromptsReply;
import com.promptlib.proto.v1.PromptReply;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.net.URI;
import java.util.List;

@Path("/api/v1/prompts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@SecurityRequirement(name = "keycloak")
public class PromptResource {

    @Inject
    PromptClient prompts;
    @Inject
    GalleryClient gallery;
    @Inject
    LibraryClient library;

    @POST
    public Response create(CreatePromptRequest request) {
        PromptReply created = prompts.create(request.title(), request.description(),
                DtoMapper.messageInputs(request.messages()),
                DtoMapper.variableInputs(request.variables()));
        return Response.created(URI.create("/api/v1/prompts/" + created.getId()))
                .entity(DtoMapper.prompt(created))
                .build();
    }

    @GET
    public PageResponse<PromptResponse> list(@QueryParam("cursor") String cursor,
                                             @QueryParam("limit") @DefaultValue("20") int limit) {
        ListPromptsReply reply = prompts.list(cursor, limit);
        return new PageResponse<>(
                reply.getItemsList().stream().map(DtoMapper::prompt).toList(),
                reply.getNextCursor().isBlank() ? null : reply.getNextCursor());
    }

    @GET
    @Path("/{id}")
    public PromptResponse get(@PathParam("id") String id) {
        return DtoMapper.prompt(prompts.get(id));
    }

    @PATCH
    @Path("/{id}")
    public PromptResponse update(@PathParam("id") String id, UpdatePromptRequest request) {
        return DtoMapper.prompt(prompts.update(id, request.title(), request.description()));
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        prompts.delete(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/favorite")
    public Response favorite(@PathParam("id") String id) {
        gallery.addFavorite(id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/favorite")
    public Response unfavorite(@PathParam("id") String id) {
        gallery.removeFavorite(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/tags")
    public List<TagResponse> setTags(@PathParam("id") String id, SetTagsRequest request) {
        return library.setTags(id, request.names()).getItemsList().stream().map(DtoMapper::tag).toList();
    }
}
