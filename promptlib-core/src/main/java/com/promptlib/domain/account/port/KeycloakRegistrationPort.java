package com.promptlib.domain.account.port;

/** Creating the Keycloak account. The only credential the application holds. */
public interface KeycloakRegistrationPort {

    /** @return the Keycloak user id, which is also the token's {@code sub} */
    String createUser(String username, String email, String displayName, String password);
}
