package com.promptlib.adapter.keycloak;

import com.promptlib.domain.account.port.KeycloakRegistrationPort;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Conflict;
import com.promptlib.shared.error.UpstreamUnavailable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class KeycloakRegistrationAdapter implements KeycloakRegistrationPort {

    @Inject
    @RestClient
    KeycloakAdminClient client;

    @Override
    public String createUser(String username, String email, String displayName, String password) {
        String first = displayName == null || displayName.isBlank() ? username : displayName.trim();
        String last = "User";
        int space = first.indexOf(' ');
        if (space > 0) {
            last = first.substring(space + 1).trim();
            first = first.substring(0, space);
        }
        CreateKeycloakUser body = new CreateKeycloakUser(username, email, first, last, true, false,
                List.of(new CreateKeycloakUser.Credential("password", password, false)));
        try (Response response = client.createUser(body)) {
            if (response.getStatus() == 409) {
                throw new Conflict(ProblemCode.USERNAME_TAKEN);
            }
            if (response.getStatus() >= 300) {
                throw new UpstreamUnavailable(ProblemCode.IDENTITY_PROVIDER_UNAVAILABLE,
                        "Keycloak rejected the registration: " + response.getStatus());
            }
            String location = response.getHeaderString("Location");
            if (location == null || !location.contains("/")) {
                throw new UpstreamUnavailable(ProblemCode.IDENTITY_PROVIDER_UNAVAILABLE,
                        "Keycloak did not return the created user id");
            }
            return location.substring(location.lastIndexOf('/') + 1);
        }
    }
}
