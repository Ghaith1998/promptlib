package com.promptlib.grpc;

import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.DomainException;
import com.promptlib.shared.error.Forbidden;
import com.promptlib.shared.error.NotFound;
import com.promptlib.shared.error.UpstreamUnavailable;
import com.promptlib.shared.error.ValidationFailed;
import io.grpc.Status;

/** One place where a domain failure becomes a gRPC status. The description carries the code. */
public final class GrpcErrors {

    private GrpcErrors() {
    }

    public static Status.Code map(DomainException e) {
        if (e instanceof NotFound) {
            return Status.Code.NOT_FOUND;
        }
        if (e instanceof Forbidden) {
            return Status.Code.PERMISSION_DENIED;
        }
        if (e instanceof Conflict) {
            return Status.Code.ABORTED;
        }
        if (e instanceof ValidationFailed) {
            return Status.Code.INVALID_ARGUMENT;
        }
        if (e instanceof UpstreamUnavailable) {
            return Status.Code.UNAVAILABLE;
        }
        return Status.Code.INTERNAL;
    }

    public static RuntimeException toStatus(DomainException e) {
        return Status.fromCode(map(e)).withDescription(e.code().name()).asRuntimeException();
    }
}
