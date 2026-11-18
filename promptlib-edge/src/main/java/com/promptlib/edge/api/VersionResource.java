package com.promptlib.edge.api;

import com.promptlib.edge.api.dto.prompt.PromptResponse;
import com.promptlib.edge.api.dto.version.CreateVersionRequest;
import com.promptlib.edge.api.dto.version.PublishRequest;
import com.promptlib.edge.api.dto.version.VersionResponse;
import com.promptlib.edge.api.dto.version.VersionSummaryResponse;
import com.promptlib.edge.client.PromptClient;
import com.promptlib.edge.client.VersionClient;
import com.promptlib.edge.mapping.DtoMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.net.URI;
import java.util.List;

@Path("/api/v1/prompts/{promptId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@SecurityRequirement(name = "keycloak")
public class VersionResource {

    @Inject
    VersionClient versions;
    @Inject
    PromptClient prompts;

    @GET
    @Path("/versions")
    public List<VersionSummaryResponse> list(@PathParam("promptId") String promptId) {
        return versions.list(promptId).getItemsList().stream().map(DtoMapper::versionSummary).toList();
    }

    @POST
    @Path("/versions")
    public Response create(@PathParam("promptId") String promptId, CreateVersionRequest request) {
        var created = versions.create(promptId, request.changeNote(),
                DtoMapper.messageInputs(request.messages()),
                DtoMapper.variableInputs(request.variables()));
        return Response.created(URI.create("/api/v1/prompts/" + promptId + "/versions/" + created.getVersionNumber()))
                .entity(DtoMapper.versionSummary(created))
                .build();
    }

    @GET
    @Path("/versions/{number}")
    public VersionResponse get(@PathParam("promptId") String promptId, @PathParam("number") int number) {
        return DtoMapper.version(versions.get(promptId, number));
    }

    @POST
    @Path("/versions/{number}/publish")
    public PromptResponse publish(@PathParam("promptId") String promptId, @PathParam("number") int number,
                                  PublishRequest request) {
        return DtoMapper.prompt(versions.publish(promptId, number, request.categoryId()));
    }

    @POST
    @Path("/unpublish")
    public PromptResponse unpublish(@PathParam("promptId") String promptId) {
        return DtoMapper.prompt(versions.unpublish(promptId));
    }

    @GET
    @Path("/detail")
    public PromptResponse detail(@PathParam("promptId") String promptId) {
        return DtoMapper.prompt(prompts.get(promptId));
    }
}
