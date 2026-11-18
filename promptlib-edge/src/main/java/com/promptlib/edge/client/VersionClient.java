package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.CreateVersionRequest;
import com.promptlib.proto.v1.GetPromptRequest;
import com.promptlib.proto.v1.GetVersionRequest;
import com.promptlib.proto.v1.ListVersionsReply;
import com.promptlib.proto.v1.ListVersionsRequest;
import com.promptlib.proto.v1.MessageInput;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.PublishVersionRequest;
import com.promptlib.proto.v1.VariableInput;
import com.promptlib.proto.v1.VersionReply;
import com.promptlib.proto.v1.VersionServiceGrpc;
import com.promptlib.proto.v1.VersionSummaryReply;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class VersionClient {

    @GrpcClient("core")
    VersionServiceGrpc.VersionServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    private VersionServiceGrpc.VersionServiceBlockingStub call() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)));
    }

    public ListVersionsReply list(String promptId) {
        return call().list(ListVersionsRequest.newBuilder().setPromptId(promptId).build());
    }

    public VersionReply get(String promptId, int versionNumber) {
        return call().get(GetVersionRequest.newBuilder()
                .setPromptId(promptId).setVersionNumber(versionNumber).build());
    }

    public VersionSummaryReply create(String promptId, String changeNote, List<MessageInput> messages,
                                      List<VariableInput> variables) {
        return call().create(CreateVersionRequest.newBuilder()
                .setPromptId(promptId).setChangeNote(changeNote == null ? "" : changeNote)
                .addAllMessages(messages).addAllVariables(variables).build());
    }

    public PromptReply publish(String promptId, int versionNumber, String categoryId) {
        return call().publish(PublishVersionRequest.newBuilder()
                .setPromptId(promptId).setVersionNumber(versionNumber).setCategoryId(categoryId).build());
    }

    public PromptReply unpublish(String promptId) {
        return call().unpublish(GetPromptRequest.newBuilder().setPromptId(promptId).build());
    }
}
