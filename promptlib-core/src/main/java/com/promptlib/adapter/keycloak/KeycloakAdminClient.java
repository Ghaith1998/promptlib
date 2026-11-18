package com.promptlib.adapter.keycloak;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** The Keycloak Admin REST API, limited to what registration needs. */
@RegisterRestClient(configKey = "keycloak-admin")
@OidcClientFilter("keycloak-admin")
public interface KeycloakAdminClient {

    @POST
    @Path("/admin/realms/promptlib/users")
    @Consumes(MediaType.APPLICATION_JSON)
    Response createUser(CreateKeycloakUser user);
}
