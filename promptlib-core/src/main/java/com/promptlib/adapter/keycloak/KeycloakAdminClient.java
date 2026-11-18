package com.promptlib.adapter.keycloak;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** The Keycloak Admin REST API, limited to what registration needs. */
@RegisterRestClient(configKey = "keycloak-admin")
@RegisterProvider(KeycloakAuthFilter.class)
public interface KeycloakAdminClient {

    @POST
    @Path("/admin/realms/promptlib/users")
    @Consumes(MediaType.APPLICATION_JSON)
    Response createUser(CreateKeycloakUser user);
}
