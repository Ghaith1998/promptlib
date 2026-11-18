package com.promptlib.grpc;

import com.promptlib.domain.gallery.service.FavoriteService;
import com.promptlib.domain.gallery.service.GalleryService;
import com.promptlib.proto.v1.BrowseReply;
import com.promptlib.proto.v1.BrowseRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.FavoriteRequest;
import com.promptlib.proto.v1.ForkReply;
import com.promptlib.proto.v1.ForkRequest;
import com.promptlib.proto.v1.GalleryServiceGrpc;
import com.promptlib.proto.v1.ListFavoritesReply;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class GalleryGrpcService extends GalleryServiceGrpc.GalleryServiceImplBase {

    @Inject
    GalleryService gallery;
    @Inject
    FavoriteService favorites;

    @Override
    public void browse(BrowseRequest request, StreamObserver<BrowseReply> observer) {
        GrpcResponses.unary(observer, () -> {
            GrpcCallers.require();
            var page = gallery.browse(GrpcCallers.nullableUuid(request.getCategoryId()),
                    request.getPage().getCursor(), request.getPage().getLimit());
            BrowseReply.Builder builder = BrowseReply.newBuilder();
            page.items().forEach(i -> builder.addItems(GrpcMappers.galleryItem(i)));
            if (page.nextCursor() != null) {
                builder.setNextCursor(page.nextCursor());
            }
            return builder.build();
        });
    }

    @Override
    public void fork(ForkRequest request, StreamObserver<ForkReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return ForkReply.newBuilder()
                    .setPromptId(gallery.fork(GrpcCallers.uuid(request.getPromptId()), caller).toString())
                    .build();
        });
    }

    @Override
    public void listFavorites(Empty request, StreamObserver<ListFavoritesReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            ListFavoritesReply.Builder builder = ListFavoritesReply.newBuilder();
            favorites.list(caller).forEach(f -> builder.addItems(GrpcMappers.favorite(f)));
            return builder.build();
        });
    }

    @Override
    public void addFavorite(FavoriteRequest request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            favorites.add(caller, GrpcCallers.uuid(request.getPromptId()));
        });
    }

    @Override
    public void removeFavorite(FavoriteRequest request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            favorites.remove(caller, GrpcCallers.uuid(request.getPromptId()));
        });
    }
}
