package com.promptlib.grpc;

import com.promptlib.shared.error.DomainException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

/** Uniform unary handling: one success path, one domain-error path, one opaque internal path. */
public final class GrpcResponses {

    private GrpcResponses() {
    }

    public static <T> void unary(StreamObserver<T> observer, Supplier<T> body) {
        try {
            observer.onNext(body.get());
            observer.onCompleted();
        } catch (DomainException e) {
            observer.onError(GrpcErrors.toStatus(e));
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL.withDescription("internal error").asRuntimeException());
        }
    }
}
