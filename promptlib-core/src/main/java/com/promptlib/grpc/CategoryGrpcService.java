package com.promptlib.grpc;

import com.promptlib.domain.category.service.CategoryAdminService;
import com.promptlib.domain.category.service.CategoryService;
import com.promptlib.proto.v1.CategoryReply;
import com.promptlib.proto.v1.CategoryRef;
import com.promptlib.proto.v1.CategoryServiceGrpc;
import com.promptlib.proto.v1.CreateCategoryRequest;
import com.promptlib.proto.v1.UpdateCategoryRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ListCategoriesReply;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import jakarta.enterprise.context.control.ActivateRequestContext;

@GrpcService
@ActivateRequestContext
public class CategoryGrpcService extends CategoryServiceGrpc.CategoryServiceImplBase {

    @Inject
    CategoryService categories;
    @Inject
    CategoryAdminService categoryAdmin;

    @Override
    public void create(CreateCategoryRequest request, StreamObserver<CategoryReply> observer) {
        GrpcResponses.unary(observer, () -> GrpcMappers.category(categoryAdmin.create(
                GrpcCallers.require(), request.getName(), request.getDescription(), request.getColor())));
    }

    @Override
    public void update(UpdateCategoryRequest request, StreamObserver<CategoryReply> observer) {
        GrpcResponses.unary(observer, () -> GrpcMappers.category(categoryAdmin.update(
                GrpcCallers.require(), GrpcCallers.uuid(request.getId()), request.getName(),
                request.getDescription(), request.getColor(), request.getSortOrder())));
    }

    @Override
    public void deactivate(CategoryRef request, StreamObserver<CategoryReply> observer) {
        GrpcResponses.unary(observer, () -> GrpcMappers.category(categoryAdmin.deactivate(
                GrpcCallers.require(), GrpcCallers.uuid(request.getId()))));
    }

    @Override
    public void list(Empty request, StreamObserver<ListCategoriesReply> observer) {
        GrpcResponses.unary(observer, () -> {
            ListCategoriesReply.Builder builder = ListCategoriesReply.newBuilder();
            categories.listActive().forEach(c -> builder.addItems(GrpcMappers.category(c)));
            return builder.build();
        });
    }
}
