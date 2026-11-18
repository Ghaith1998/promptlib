package com.promptlib.edge.client;

import com.promptlib.edge.security.RequestIdentity;
import io.grpc.Metadata;

import java.util.Set;

/** The metadata contract expected by core's CallerContextInterceptor. */
public final class GrpcMetadata {

    public static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> USER_SUB =
            Metadata.Key.of("x-user-sub", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> USER_ROLES =
            Metadata.Key.of("x-user-roles", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcMetadata() {
    }

    public static Metadata of(RequestIdentity identity) {
        Metadata metadata = new Metadata();
        if (identity.userId() != null) {
            metadata.put(USER_ID, identity.userId().toString());
        }
        if (identity.sub() != null) {
            metadata.put(USER_SUB, identity.sub());
        }
        metadata.put(USER_ROLES, String.join(",", identity.roles()));
        if (identity.requestId() != null) {
            metadata.put(REQUEST_ID, identity.requestId());
        }
        return metadata;
    }

    public static Metadata forRegistration(String sub, Set<String> roles) {
        Metadata metadata = new Metadata();
        if (sub != null) {
            metadata.put(USER_SUB, sub);
        }
        metadata.put(USER_ROLES, String.join(",", roles));
        return metadata;
    }
}
