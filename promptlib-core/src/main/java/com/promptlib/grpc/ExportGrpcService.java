package com.promptlib.grpc;

import com.promptlib.domain.export.service.ExportService;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.ExportChunk;
import com.promptlib.proto.v1.ExportPromptRef;
import com.promptlib.proto.v1.ExportServiceGrpc;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@GrpcService
@Blocking
@ActivateRequestContext
public class ExportGrpcService extends ExportServiceGrpc.ExportServiceImplBase {

    @Inject
    ExportService exports;

    @Override
    public void exportLibrary(Empty request, StreamObserver<ExportChunk> observer) {
        stream(observer, () -> exports.exportLibrary(GrpcCallers.require()));
    }

    @Override
    public void exportMarkdown(ExportPromptRef request, StreamObserver<ExportChunk> observer) {
        stream(observer, () -> exports.exportMarkdown(GrpcCallers.require(),
                GrpcCallers.uuid(request.getPromptId())));
    }

    private void stream(StreamObserver<ExportChunk> observer, java.util.function.Supplier<java.util.List<String>> body) {
        try {
            body.get().forEach(chunk -> observer.onNext(ExportChunk.newBuilder().setData(chunk).build()));
            observer.onCompleted();
        } catch (com.promptlib.shared.error.DomainException e) {
            observer.onError(GrpcErrors.toStatus(e));
        } catch (RuntimeException e) {
            observer.onError(io.grpc.Status.INTERNAL.withDescription("internal error").asRuntimeException());
        }
    }
}
