package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.gallery.FavoriteItemResponse;
import com.promptlib.edge.client.GalleryClient;
import com.promptlib.edge.mapping.DtoMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

@Path("/api/v1/favorites")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@SecurityRequirement(name = "keycloak")
public class FavoriteResource {

    @Inject
    GalleryClient gallery;

    @GET
    public List<FavoriteItemResponse> list() {
        return gallery.listFavorites().getItemsList().stream().map(DtoMapper::favorite).toList();
    }
}
