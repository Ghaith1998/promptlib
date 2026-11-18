package com.promptlib.grpc.interceptor;

import com.promptlib.grpc.CallerContextKey;
import com.promptlib.shared.CallerContext;
import io.grpc.Context;
import io.grpc.Contexts;
import io.quarkus.grpc.GlobalInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts the edge's metadata into a CallerContext. Absence of x-user-id is allowed for
 * registration, where the user does not exist yet; every other RPC rejects it.
 */
@GlobalInterceptor
@ApplicationScoped
public class CallerContextInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_SUB =
            Metadata.Key.of("x-user-sub", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ROLES =
            Metadata.Key.of("x-user-roles", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String rawId = headers.get(USER_ID);
        String sub = headers.get(USER_SUB);
        String rawRoles = headers.get(USER_ROLES);

        UUID userId = null;
        if (rawId != null && !rawId.isBlank()) {
            try {
                userId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                call.close(Status.UNAUTHENTICATED.withDescription("invalid user id"), new Metadata());
                return new ServerCall.Listener<>() {
                };
            }
        }

        Set<String> roles = rawRoles == null || rawRoles.isBlank()
                ? Set.of()
                : Arrays.stream(rawRoles.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());

        Context context = Context.current().withValue(
                CallerContextKey.KEY, new CallerContext(userId, sub, roles));
        return Contexts.interceptCall(context, call, headers, next);
    }
}
