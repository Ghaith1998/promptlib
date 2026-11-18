package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.CreatePromptRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.GetPromptRequest;
import com.promptlib.proto.v1.ListPromptsReply;
import com.promptlib.proto.v1.ListPromptsRequest;
import com.promptlib.proto.v1.MessageInput;
import com.promptlib.proto.v1.PageRequest;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.PromptServiceGrpc;
import com.promptlib.proto.v1.UpdatePromptRequest;
import com.promptlib.proto.v1.VariableInput;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PromptClient {

    @GrpcClient("core")
    PromptServiceGrpc.PromptServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    private PromptServiceGrpc.PromptServiceBlockingStub call() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)));
    }

    public PromptReply create(String title, String description, List<MessageInput> messages,
                              List<VariableInput> variables) {
        return call().create(CreatePromptRequest.newBuilder()
                .setTitle(title).setDescription(description)
                .addAllMessages(messages).addAllVariables(variables).build());
    }

    public PromptReply get(String promptId) {
        return call().get(GetPromptRequest.newBuilder().setPromptId(promptId).build());
    }

    public ListPromptsReply list(String cursor, int limit, String query) {
        return call().list(ListPromptsRequest.newBuilder()
                .setPage(PageRequest.newBuilder().setCursor(cursor == null ? "" : cursor).setLimit(limit))
                .setQuery(query == null ? "" : query)
                .build());
    }

    public PromptReply update(String promptId, String title, String description) {
        return call().updateMetadata(UpdatePromptRequest.newBuilder()
                .setPromptId(promptId).setTitle(title).setDescription(description).build());
    }

    public void delete(String promptId) {
        call().delete(GetPromptRequest.newBuilder().setPromptId(promptId).build());
    }

    @SuppressWarnings("unused")
    private static final Empty EMPTY = Empty.getDefaultInstance();
}
