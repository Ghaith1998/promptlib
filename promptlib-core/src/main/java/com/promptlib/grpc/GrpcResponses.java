package com.promptlib.grpc;

import com.promptlib.shared.error.DomainException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.jboss.logging.Logger;

import java.util.function.Supplier;

/** Uniform unary handling: success, domain error, transport error, opaque internal error. */
public final class GrpcResponses {

    private static final Logger LOG = Logger.getLogger(GrpcResponses.class);

    private GrpcResponses() {
    }

    public static <T> void unary(StreamObserver<T> observer, Supplier<T> body) {
        try {
            observer.onNext(body.get());
            observer.onCompleted();
        } catch (DomainException e) {
            observer.onError(GrpcErrors.toStatus(e));
        } catch (StatusRuntimeException e) {
            observer.onError(e);
        } catch (RuntimeException e) {
            LOG.error("gRPC call failed", e);
            observer.onError(Status.INTERNAL.withDescription("internal error").asRuntimeException());
        }
    }

    public static void run(StreamObserver<com.promptlib.proto.v1.Empty> observer, Runnable body) {
        try {
            body.run();
            observer.onNext(com.promptlib.proto.v1.Empty.getDefaultInstance());
            observer.onCompleted();
        } catch (DomainException e) {
            observer.onError(GrpcErrors.toStatus(e));
        } catch (StatusRuntimeException e) {
            observer.onError(e);
        } catch (RuntimeException e) {
            LOG.error("gRPC call failed", e);
            observer.onError(Status.INTERNAL.withDescription("internal error").asRuntimeException());
        }
    }
}
