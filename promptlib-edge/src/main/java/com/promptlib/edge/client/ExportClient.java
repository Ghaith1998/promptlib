package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ExportChunk;
import com.promptlib.proto.v1.ExportPromptRef;
import com.promptlib.proto.v1.ExportServiceGrpc;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Iterator;

@ApplicationScoped
public class ExportClient {

    @GrpcClient("core")
    ExportServiceGrpc.ExportServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    public Iterator<ExportChunk> library() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .exportLibrary(Empty.getDefaultInstance());
    }

    public Iterator<ExportChunk> markdown(String promptId) {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .exportMarkdown(ExportPromptRef.newBuilder().setPromptId(promptId).build());
    }
}
