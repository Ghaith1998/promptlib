package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.AdminServiceGrpc;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.HideRequest;
import com.promptlib.proto.v1.ListAdminPromptsReply;
import com.promptlib.proto.v1.UnhideRequest;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminClient {

    @GrpcClient("core")
    AdminServiceGrpc.AdminServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    public ListAdminPromptsReply listPrompts() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .listPrompts(Empty.getDefaultInstance());
    }

    public void hide(String promptId, String reason) {
        stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .hide(HideRequest.newBuilder().setPromptId(promptId).setReason(reason).build());
    }

    public void unhide(String promptId) {
        stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .unhide(UnhideRequest.newBuilder().setPromptId(promptId).build());
    }
}
