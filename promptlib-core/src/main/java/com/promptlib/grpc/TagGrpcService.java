package com.promptlib.grpc;

import com.promptlib.domain.library.service.TagService;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ListTagsReply;
import com.promptlib.proto.v1.SetTagsRequest;
import com.promptlib.proto.v1.TagRef;
import com.promptlib.proto.v1.TagServiceGrpc;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import jakarta.enterprise.context.control.ActivateRequestContext;

@GrpcService
@ActivateRequestContext
public class TagGrpcService extends TagServiceGrpc.TagServiceImplBase {

    @Inject
    TagService tags;

    @Override
    public void list(Empty request, StreamObserver<ListTagsReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            ListTagsReply.Builder builder = ListTagsReply.newBuilder();
            tags.list(caller).forEach(t -> builder.addItems(GrpcMappers.tag(t)));
            return builder.build();
        });
    }

    @Override
    public void setForPrompt(SetTagsRequest request, StreamObserver<ListTagsReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            ListTagsReply.Builder builder = ListTagsReply.newBuilder();
            tags.setForPrompt(caller, GrpcCallers.uuid(request.getPromptId()), request.getNamesList())
                    .forEach(t -> builder.addItems(GrpcMappers.tag(t)));
            return builder.build();
        });
    }

    @Override
    public void delete(TagRef request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            tags.delete(caller, GrpcCallers.uuid(request.getTagId()));
        });
    }
}
