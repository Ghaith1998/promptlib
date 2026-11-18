package com.promptlib.grpc;

import com.promptlib.domain.library.service.CollectionService;
import com.promptlib.proto.v1.AddPromptRequest;
import com.promptlib.proto.v1.CollectionRef;
import com.promptlib.proto.v1.CollectionReply;
import com.promptlib.proto.v1.CollectionServiceGrpc;
import com.promptlib.proto.v1.CreateCollectionRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ListCollectionsReply;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class CollectionGrpcService extends CollectionServiceGrpc.CollectionServiceImplBase {

    @Inject
    CollectionService collections;

    @Override
    public void list(Empty request, StreamObserver<ListCollectionsReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            ListCollectionsReply.Builder builder = ListCollectionsReply.newBuilder();
            collections.list(caller).forEach(c -> builder.addItems(GrpcMappers.collection(c)));
            return builder.build();
        });
    }

    @Override
    public void create(CreateCollectionRequest request, StreamObserver<CollectionReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return GrpcMappers.collection(collections.create(caller, request.getName(), null,
                    request.getColor(), null));
        });
    }

    @Override
    public void delete(CollectionRef request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            collections.delete(caller, GrpcCallers.uuid(request.getCollectionId()));
        });
    }

    @Override
    public void addPrompt(AddPromptRequest request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            collections.addPrompt(caller, GrpcCallers.uuid(request.getCollectionId()),
                    GrpcCallers.uuid(request.getPromptId()));
        });
    }
}
