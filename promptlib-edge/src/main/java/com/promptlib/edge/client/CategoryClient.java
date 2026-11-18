package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.CategoryRef;
import com.promptlib.proto.v1.CategoryReply;
import com.promptlib.proto.v1.CategoryServiceGrpc;
import com.promptlib.proto.v1.CreateCategoryRequest;
import com.promptlib.proto.v1.UpdateCategoryRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ListCategoriesReply;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryClient {

    @GrpcClient("core")
    CategoryServiceGrpc.CategoryServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    public CategoryReply create(String name, String description, String color) {
        return withHeaders().create(CreateCategoryRequest.newBuilder()
                .setName(name).setDescription(description == null ? "" : description)
                .setColor(color == null ? "" : color).build());
    }

    public CategoryReply update(String id, String name, String description, String color, Integer sortOrder) {
        return withHeaders().update(UpdateCategoryRequest.newBuilder()
                .setId(id).setName(name == null ? "" : name)
                .setDescription(description == null ? "" : description)
                .setColor(color == null ? "" : color)
                .setSortOrder(sortOrder == null ? 0 : sortOrder).build());
    }

    public CategoryReply deactivate(String id) {
        return withHeaders().deactivate(CategoryRef.newBuilder().setId(id).build());
    }

    private CategoryServiceGrpc.CategoryServiceBlockingStub withHeaders() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)));
    }

    public ListCategoriesReply list() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .list(Empty.getDefaultInstance());
    }
}
