package com.promptlib.grpc;

import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.service.VersionService;
import com.promptlib.proto.v1.CreateVersionRequest;
import com.promptlib.proto.v1.GetPromptRequest;
import com.promptlib.proto.v1.GetVersionRequest;
import com.promptlib.proto.v1.ListVersionsReply;
import com.promptlib.proto.v1.ListVersionsRequest;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.PublishVersionRequest;
import com.promptlib.proto.v1.VersionReply;
import com.promptlib.proto.v1.VersionServiceGrpc;
import com.promptlib.proto.v1.VersionSummaryReply;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.control.ActivateRequestContext;

@GrpcService
@Blocking
@ActivateRequestContext
public class VersionGrpcService extends VersionServiceGrpc.VersionServiceImplBase {

    @Inject
    VersionService versions;

    @Override
    public void list(ListVersionsRequest request, StreamObserver<ListVersionsReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            ListVersionsReply.Builder builder = ListVersionsReply.newBuilder();
            versions.list(GrpcCallers.uuid(request.getPromptId()), caller)
                    .forEach(v -> builder.addItems(GrpcMappers.versionSummary(v)));
            return builder.build();
        });
    }

    @Override
    public void get(GetVersionRequest request, StreamObserver<VersionReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            var promptId = GrpcCallers.uuid(request.getPromptId());
            PromptVersion version = versions.get(promptId, request.getVersionNumber(), caller);
            return GrpcMappers.version(version, versions.messages(version.id, promptId, caller));
        });
    }

    @Override
    public void create(CreateVersionRequest request, StreamObserver<VersionSummaryReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            PromptVersion version = versions.createVersion(
                    GrpcCallers.uuid(request.getPromptId()), caller, request.getChangeNote(),
                    GrpcMappers.messages(request.getMessagesList()),
                    GrpcMappers.variables(request.getVariablesList()));
            return GrpcMappers.versionSummary(version);
        });
    }

    @Override
    public void publish(PublishVersionRequest request, StreamObserver<PromptReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return GrpcMappers.prompt(versions.publish(
                    GrpcCallers.uuid(request.getPromptId()), request.getVersionNumber(),
                    GrpcCallers.uuid(request.getCategoryId()), caller));
        });
    }

    @Override
    public void unpublish(GetPromptRequest request, StreamObserver<PromptReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return GrpcMappers.prompt(versions.unpublish(
                    GrpcCallers.uuid(request.getPromptId()), caller));
        });
    }
}
