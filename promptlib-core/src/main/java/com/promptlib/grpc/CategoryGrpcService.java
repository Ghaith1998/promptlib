package com.promptlib.grpc;

import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.proto.v1.CategoryServiceGrpc;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ListCategoriesReply;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class CategoryGrpcService extends CategoryServiceGrpc.CategoryServiceImplBase {

    @Inject
    CategoryService categories;

    @Override
    public void list(Empty request, StreamObserver<ListCategoriesReply> observer) {
        GrpcResponses.unary(observer, () -> {
            ListCategoriesReply.Builder builder = ListCategoriesReply.newBuilder();
            categories.listActive().forEach(c -> builder.addItems(GrpcMappers.category(c)));
            return builder.build();
        });
    }
}
