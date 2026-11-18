package com.promptlib.grpc;

import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptDraft;
import com.promptlib.domain.prompt.service.PromptService;
import com.promptlib.proto.v1.CreatePromptRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.GetPromptRequest;
import com.promptlib.proto.v1.ListPromptsReply;
import com.promptlib.proto.v1.ListPromptsRequest;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.PromptServiceGrpc;
import com.promptlib.proto.v1.UpdatePromptRequest;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Page;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class PromptGrpcService extends PromptServiceGrpc.PromptServiceImplBase {

    @Inject
    PromptService prompts;

    @Override
    public void create(CreatePromptRequest request, StreamObserver<PromptReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            PromptDraft draft = new PromptDraft(request.getTitle(), request.getDescription(),
                    GrpcMappers.messages(request.getMessagesList()),
                    GrpcMappers.variables(request.getVariablesList()));
            return GrpcMappers.prompt(prompts.create(caller, draft));
        });
    }

    @Override
    public void get(GetPromptRequest request, StreamObserver<PromptReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return GrpcMappers.prompt(prompts.getOwned(caller, GrpcCallers.uuid(request.getPromptId())));
        });
    }

    @Override
    public void list(ListPromptsRequest request, StreamObserver<ListPromptsReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            Page<Prompt> page = prompts.listOwned(caller, request.getPage().getCursor(),
                    request.getPage().getLimit());
            ListPromptsReply.Builder builder = ListPromptsReply.newBuilder();
            page.items().forEach(p -> builder.addItems(GrpcMappers.prompt(p)));
            if (page.nextCursor() != null) {
                builder.setNextCursor(page.nextCursor());
            }
            return builder.build();
        });
    }

    @Override
    public void updateMetadata(UpdatePromptRequest request, StreamObserver<PromptReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return GrpcMappers.prompt(prompts.updateMetadata(caller,
                    GrpcCallers.uuid(request.getPromptId()), request.getTitle(), request.getDescription()));
        });
    }

    @Override
    public void delete(GetPromptRequest request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            prompts.delete(caller, GrpcCallers.uuid(request.getPromptId()));
        });
    }
}
