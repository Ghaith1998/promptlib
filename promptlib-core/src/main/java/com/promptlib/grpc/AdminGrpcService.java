package com.promptlib.grpc;

import com.promptlib.domain.admin.service.AdminService;
import com.promptlib.domain.admin.service.ModerationService;
import com.promptlib.proto.v1.AdminServiceGrpc;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.HideRequest;
import com.promptlib.proto.v1.ListAdminPromptsReply;
import com.promptlib.proto.v1.UnhideRequest;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.control.ActivateRequestContext;

@GrpcService
@Blocking
@ActivateRequestContext
public class AdminGrpcService extends AdminServiceGrpc.AdminServiceImplBase {

    @Inject
    ModerationService moderation;
    @Inject
    AdminService admin;

    @Override
    public void listPrompts(Empty request, StreamObserver<ListAdminPromptsReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            ListAdminPromptsReply.Builder builder = ListAdminPromptsReply.newBuilder();
            admin.listPrompts(caller).forEach(r -> builder.addItems(GrpcMappers.adminPrompt(r)));
            return builder.build();
        });
    }

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
