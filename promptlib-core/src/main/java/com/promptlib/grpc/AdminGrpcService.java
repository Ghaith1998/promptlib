package com.promptlib.grpc;

import com.promptlib.domain.admin.service.ModerationService;
import com.promptlib.proto.v1.AdminServiceGrpc;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.HideRequest;
import com.promptlib.proto.v1.UnhideRequest;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class AdminGrpcService extends AdminServiceGrpc.AdminServiceImplBase {

    @Inject
    ModerationService moderation;

    @Override
    public void hide(HideRequest request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            moderation.hide(caller, GrpcCallers.uuid(request.getPromptId()), request.getReason(), null);
        });
    }

    @Override
    public void unhide(UnhideRequest request, StreamObserver<Empty> observer) {
        GrpcResponses.run(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            moderation.unhide(caller, GrpcCallers.uuid(request.getPromptId()), null);
        });
    }
}
