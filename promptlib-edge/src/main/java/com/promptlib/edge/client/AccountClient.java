package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.AccountServiceGrpc;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.EnsureUserRequest;
import com.promptlib.proto.v1.RegisterRequest;
import com.promptlib.proto.v1.UpdateProfileRequest;
import com.promptlib.proto.v1.UserReply;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class AccountClient {

    @GrpcClient("core")
    AccountServiceGrpc.AccountServiceBlockingStub stub;

    @Inject
    RequestIdentity identity;

    public UserReply ensureUser(String sub, String username, String email, Set<String> roles) {
        EnsureUserRequest request = EnsureUserRequest.newBuilder()
                .setSub(sub).setUsername(username).setEmail(email).addAllRoles(roles).build();
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(
                        GrpcMetadata.forRegistration(sub, roles)))
                .ensureUser(request);
    }

    public UserReply register(String username, String email, String displayName, String password) {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(
                        GrpcMetadata.forRegistration(null, java.util.Set.of())))
                .register(RegisterRequest.newBuilder()
                        .setUsername(username).setEmail(email)
                        .setDisplayName(displayName == null ? "" : displayName)
                        .setPassword(password).build());
    }

    public UserReply getMe() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .getMe(Empty.getDefaultInstance());
    }

    public UserReply updateProfile(String displayName) {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .updateProfile(UpdateProfileRequest.newBuilder().setDisplayName(displayName).build());
    }
}
