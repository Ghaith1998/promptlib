package com.promptlib.grpc;

import com.promptlib.shared.CallerContext;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;
import io.grpc.Status;

import java.util.UUID;

public final class GrpcCallers {

    private GrpcCallers() {
    }

    public static CallerContext require() {
        CallerContext caller = CallerContextKey.get();
        if (caller == null || caller.userId() == null) {
            throw Status.UNAUTHENTICATED.withDescription("missing caller identity").asRuntimeException();
        }
        return caller;
    }

    public static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            throw new ValidationFailed(ProblemCode.VALIDATION_FAILED, "invalid id " + value);
        }
    }

    public static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : uuid(value);
    }
}
