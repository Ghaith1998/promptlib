package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.AddPromptRequest;
import com.promptlib.proto.v1.CollectionRef;
import com.promptlib.proto.v1.CollectionReply;
import com.promptlib.proto.v1.CollectionServiceGrpc;
import com.promptlib.proto.v1.CreateCollectionRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ListCollectionsReply;
import com.promptlib.proto.v1.ListTagsReply;
import com.promptlib.proto.v1.SetTagsRequest;
import com.promptlib.proto.v1.TagRef;
import com.promptlib.proto.v1.TagServiceGrpc;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class LibraryClient {

    @GrpcClient("core")
    CollectionServiceGrpc.CollectionServiceBlockingStub collections;
    @GrpcClient("core")
    TagServiceGrpc.TagServiceBlockingStub tags;

    @Inject
    RequestIdentity identity;

    public ListCollectionsReply listCollections() {
        return collections.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .list(Empty.getDefaultInstance());
    }

    public CollectionReply createCollection(String name, String color) {
        return collections.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .create(CreateCollectionRequest.newBuilder()
                        .setName(name).setColor(color == null ? "" : color).build());
    }

    public void deleteCollection(String collectionId) {
        collections.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .delete(CollectionRef.newBuilder().setCollectionId(collectionId).build());
    }

    public void addPrompt(String collectionId, String promptId) {
        collections.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .addPrompt(AddPromptRequest.newBuilder()
                        .setCollectionId(collectionId).setPromptId(promptId).build());
    }

    public ListTagsReply listTags() {
        return tags.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .list(Empty.getDefaultInstance());
    }

    public ListTagsReply setTags(String promptId, List<String> names) {
        return tags.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .setForPrompt(SetTagsRequest.newBuilder().setPromptId(promptId).addAllNames(names).build());
    }

    public void deleteTag(String tagId) {
        tags.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .delete(TagRef.newBuilder().setTagId(tagId).build());
    }
}
