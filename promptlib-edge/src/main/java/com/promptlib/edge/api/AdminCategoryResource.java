package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.admin.CategoryCreateRequest;
import com.promptlib.edge.api.dto.admin.CategoryUpdateRequest;
import com.promptlib.edge.api.dto.library.CategoryResponse;
import com.promptlib.edge.client.CategoryClient;
import com.promptlib.edge.mapping.DtoMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.net.URI;

@Path("/api/v1/admin/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@SecurityRequirement(name = "keycloak")
public class AdminCategoryResource {

    @Inject
    CategoryClient categories;

    @POST
    public Response create(CategoryCreateRequest request) {
        CategoryResponse created = DtoMapper.category(
                categories.create(request.name(), request.description(), request.color()));
        return Response.created(URI.create("/api/v1/categories/" + created.id()))
                .entity(created)
                .build();
    }

    @PATCH
    @Path("/{id}")
    public CategoryResponse update(@PathParam("id") String id, CategoryUpdateRequest request) {
        return DtoMapper.category(categories.update(id, request.name(), request.description(),
                request.color(), request.sortOrder()));
    }

    @DELETE
    @Path("/{id}")
    public CategoryResponse deactivate(@PathParam("id") String id) {
        return DtoMapper.category(categories.deactivate(id));
    }
}
