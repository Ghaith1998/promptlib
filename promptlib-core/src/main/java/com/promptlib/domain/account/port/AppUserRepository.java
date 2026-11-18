package com.promptlib.domain.account.port;

import com.promptlib.domain.account.model.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository {

    Optional<AppUser> findByKeycloakSub(String sub);

    Optional<AppUser> byId(UUID id);

    void persist(AppUser user);

    void flush();
}
