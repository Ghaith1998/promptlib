package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.CategoryServiceGrpc;
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

    public ListCategoriesReply list() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .list(Empty.getDefaultInstance());
    }
}
