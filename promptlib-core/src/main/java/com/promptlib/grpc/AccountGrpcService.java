package com.promptlib.grpc;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.domain.account.service.RegistrationService;
import com.promptlib.proto.v1.AccountServiceGrpc;
import com.promptlib.proto.v1.EnsureUserRequest;
import com.promptlib.proto.v1.RegisterRequest;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.UpdateProfileRequest;
import com.promptlib.proto.v1.UserReply;
import com.promptlib.shared.CallerContext;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.control.ActivateRequestContext;

@GrpcService
@Blocking
@ActivateRequestContext
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    @Inject
    AccountService accounts;
    @Inject
    RegistrationService registrations;

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

    @Override
    public void register(RegisterRequest request, StreamObserver<UserReply> observer) {
        GrpcResponses.unary(observer, () -> {
            var registered = registrations.register(request.getUsername(), request.getEmail(),
                    request.getDisplayName(), request.getPassword());
            return UserReply.newBuilder()
                    .setId(registered.id().toString())
                    .setUsername(registered.username())
                    .setEmail(registered.email())
                    .setDisplayName(request.getDisplayName())
                    .build();
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
