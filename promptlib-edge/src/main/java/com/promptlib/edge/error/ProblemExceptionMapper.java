package com.promptlib.edge.error;

import com.promptlib.edge.api.dto.common.ProblemResponse;
import com.promptlib.edge.security.RequestIdentity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** RFC 9457-shaped errors. The gRPC status description carries the stable ProblemCode. */
@Provider
public class ProblemExceptionMapper implements ExceptionMapper<Throwable> {

    @Context
    UriInfo uriInfo;

    @Inject
    RequestIdentity identity;

    @Override
    public Response toResponse(Throwable throwable) {
        if (throwable instanceof WebApplicationException web) {
            return Response.fromResponse(web.getResponse()).build();
        }
        if (throwable instanceof StatusRuntimeException grpc) {
            Status.Code code = grpc.getStatus().getCode();
            String problemCode = grpc.getStatus().getDescription() == null
                    ? code.name()
                    : grpc.getStatus().getDescription();
            return problem(httpStatus(code), problemCode, uriInfo.getPath());
        }
        return problem(500, "INTERNAL", uriInfo.getPath());
    }

    private int httpStatus(Status.Code code) {
        return switch (code) {
            case NOT_FOUND -> 404;
            case PERMISSION_DENIED -> 403;
            case UNAUTHENTICATED -> 401;
            case ABORTED, ALREADY_EXISTS -> 409;
            case INVALID_ARGUMENT, FAILED_PRECONDITION -> 422;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> 503;
            default -> 500;
        };
    }

    private Response problem(int status, String code, String path) {
        return Response.status(status)
                .type("application/problem+json")
                .entity(new ProblemResponse("about:blank", code, status, null, path, code,
                        identity.requestId()))
                .build();
    }
}
