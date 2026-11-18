package com.promptlib.grpc;

import com.promptlib.shared.CallerContext;
import io.grpc.Context;

/** Identity travels in the gRPC Context, so services need no request-scoped holder. */
public final class CallerContextKey {

    public static final Context.Key<CallerContext> KEY = Context.key("promptlib-caller");

    private CallerContextKey() {
    }

    public static CallerContext get() {
        return KEY.get();
    }
}
