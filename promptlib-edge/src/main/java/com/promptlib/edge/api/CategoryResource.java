package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.library.CategoryResponse;
import com.promptlib.edge.client.CategoryClient;
import com.promptlib.edge.mapping.DtoMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

@Path("/api/v1/categories")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "keycloak")
public class CategoryResource {

    @Inject
    CategoryClient categories;

    @GET
    public List<CategoryResponse> list() {
        return categories.list().getItemsList().stream().map(DtoMapper::category).toList();
    }
}
