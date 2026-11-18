package com.promptlib.edge.api;

import com.promptlib.edge.client.ExportClient;
import com.promptlib.proto.v1.ExportChunk;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@Path("/api/v1")
@Authenticated
@SecurityRequirement(name = "keycloak")
public class ExportResource {

    @Inject
    ExportClient exports;

    @GET
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportLibrary() {
        return Response.ok((StreamingOutput) out -> write(out, exports.library()))
                .header("Content-Disposition", "attachment; filename=\"promptlib-export.json\"")
                .build();
    }

    @GET
    @Path("/prompts/{id}/export.md")
    @Produces("text/markdown")
    public Response exportMarkdown(@PathParam("id") String id) {
        return Response.ok((StreamingOutput) out -> write(out, exports.markdown(id)))
                .header("Content-Disposition", "attachment; filename=\"prompt-" + id + ".md\"")
                .build();
    }

    private void write(java.io.OutputStream out, Iterator<ExportChunk> chunks) throws java.io.IOException {
        while (chunks.hasNext()) {
            out.write(chunks.next().getData().getBytes(StandardCharsets.UTF_8));
        }
    }
}
