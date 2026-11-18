package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import com.promptlib.proto.v1.AccountServiceGrpc;
import com.promptlib.proto.v1.Empty;
import com.promptlib.proto.v1.EnsureUserRequest;
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

    public UserReply getMe() {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .getMe(Empty.getDefaultInstance());
    }

    public UserReply updateProfile(String displayName) {
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(GrpcMetadata.of(identity)))
                .updateProfile(UpdateProfileRequest.newBuilder().setDisplayName(displayName).build());
    }
}
