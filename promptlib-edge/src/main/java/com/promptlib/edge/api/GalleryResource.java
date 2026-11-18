package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.common.PageResponse;
import com.promptlib.edge.api.dto.gallery.ForkResponse;
import com.promptlib.edge.api.dto.gallery.GalleryItemResponse;
import com.promptlib.edge.client.GalleryClient;
import com.promptlib.edge.mapping.DtoMapper;
import com.promptlib.proto.v1.BrowseReply;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/v1/gallery")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "keycloak")
public class GalleryResource {

    @Inject
    GalleryClient gallery;

    @GET
    public PageResponse<GalleryItemResponse> browse(@QueryParam("cursor") String cursor,
                                                    @QueryParam("limit") @DefaultValue("20") int limit,
                                                    @QueryParam("categoryId") String categoryId) {
        BrowseReply reply = gallery.browse(cursor, limit, categoryId);
        return new PageResponse<>(
                reply.getItemsList().stream().map(DtoMapper::galleryItem).toList(),
                reply.getNextCursor().isBlank() ? null : reply.getNextCursor());
    }

    @POST
    @Path("/{id}/fork")
    public Response fork(@PathParam("id") String id) {
        return Response.status(Response.Status.CREATED)
                .entity(new ForkResponse(gallery.fork(id).getPromptId()))
                .build();
    }
}
