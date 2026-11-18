package com.promptlib.grpc;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.proto.v1.AccountServiceGrpc;
import com.promptlib.proto.v1.EnsureUserRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.UpdateProfileRequest;
import com.promptlib.proto.v1.UserReply;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import jakarta.enterprise.context.control.ActivateRequestContext;

@GrpcService
@ActivateRequestContext
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    @Inject
    AccountService accounts;

    @Override
    public void ensureUser(EnsureUserRequest request, StreamObserver<UserReply> observer) {
        GrpcResponses.unary(observer, () -> {
            AppUser user = accounts.ensureUser(request.getSub(), request.getUsername(), request.getEmail());
            return reply(user, request.getRolesList());
        });
    }

    @Override
    public void getMe(Empty request, StreamObserver<UserReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            return reply(accounts.require(caller.userId()), caller.roles().stream().toList());
        });
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<UserReply> observer) {
        GrpcResponses.unary(observer, () -> {
            CallerContext caller = GrpcCallers.require();
            AppUser user = accounts.updateDisplayName(caller.userId(), request.getDisplayName());
            return reply(user, caller.roles().stream().toList());
        });
    }

    private UserReply reply(AppUser user, java.util.List<String> roles) {
        return UserReply.newBuilder()
                .setId(user.id.toString())
                .setUsername(GrpcMappers.nz(user.username))
                .setEmail(GrpcMappers.nz(user.email))
                .setDisplayName(GrpcMappers.nz(user.displayName))
                .addAllRoles(roles)
                .build();
    }
}
