package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.BrowseReply;
import com.promptlib.proto.v1.BrowseRequest;
import com.promptlib.proto.v1.FavoriteRequest;
import com.promptlib.proto.v1.ForkReply;
import com.promptlib.proto.v1.ForkRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.GalleryServiceGrpc;
import com.promptlib.proto.v1.ListFavoritesReply;
import com.promptlib.proto.v1.PageRequest;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GalleryClient {

    @GrpcClient("core")
    GalleryServiceGrpc.GalleryServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    private GalleryServiceGrpc.GalleryServiceBlockingStub call() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)));
    }

    public BrowseReply browse(String cursor, int limit, String categoryId) {
        return call().browse(BrowseRequest.newBuilder()
                .setPage(PageRequest.newBuilder().setCursor(cursor == null ? "" : cursor).setLimit(limit))
                .setCategoryId(categoryId == null ? "" : categoryId)
                .build());
    }

    public ForkReply fork(String promptId) {
        return call().fork(ForkRequest.newBuilder().setPromptId(promptId).build());
    }

    public ListFavoritesReply listFavorites() {
        return call().listFavorites(Empty.getDefaultInstance());
    }

    public void addFavorite(String promptId) {
        call().addFavorite(FavoriteRequest.newBuilder().setPromptId(promptId).build());
    }

    public void removeFavorite(String promptId) {
        call().removeFavorite(FavoriteRequest.newBuilder().setPromptId(promptId).build());
    }
}
