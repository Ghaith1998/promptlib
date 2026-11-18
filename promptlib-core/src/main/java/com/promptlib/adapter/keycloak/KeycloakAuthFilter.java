package com.promptlib.adapter.keycloak;

import io.quarkus.oidc.client.NamedOidcClient;
import io.quarkus.oidc.client.OidcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Attaches the service-account token. Explicit rather than @OidcClientFilter so the
 * token acquisition is visible and testable.
 */
@ApplicationScoped
public class KeycloakAuthFilter implements ClientRequestFilter {

    @Inject
    @NamedOidcClient("keycloak-admin")
    OidcClient oidcClient;

    @Override
    public void filter(ClientRequestContext requestContext) {
        var tokens = oidcClient.getTokens().await().indefinitely();
        requestContext.getHeaders().putSingle("Authorization", "Bearer " + tokens.getAccessToken());
    }
}
