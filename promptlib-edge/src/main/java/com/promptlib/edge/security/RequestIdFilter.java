package com.promptlib.edge.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;

/** One id per request, echoed to the client and threaded through gRPC metadata. */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER = "X-Request-Id";
    private static final String PROPERTY = "promptlib.requestId";

    @Inject
    RequestIdentity identity;

    @Override
    public void filter(ContainerRequestContext request) {
        String incoming = request.getHeaderString(HEADER);
        String id = valid(incoming) ? incoming : UUID.randomUUID().toString();
        request.setProperty(PROPERTY, id);
        identity.requestId(id);
        MDC.put("requestId", id);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object id = request.getProperty(PROPERTY);
        if (id != null) {
            response.getHeaders().putSingle(HEADER, id);
        }
        MDC.remove("requestId");
    }

    private boolean valid(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
