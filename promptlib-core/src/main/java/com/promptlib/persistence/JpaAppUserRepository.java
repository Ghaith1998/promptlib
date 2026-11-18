package com.promptlib.persistence;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.port.AppUserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaAppUserRepository implements AppUserRepository {

    @Inject
    EntityManager em;

    @Override
    public Optional<AppUser> findByKeycloakSub(String sub) {
        return em.createQuery("select u from AppUser u where u.keycloakSub = :sub", AppUser.class)
                .setParameter("sub", sub)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<AppUser> byId(UUID id) {
        return Optional.ofNullable(em.find(AppUser.class, id));
    }

    @Override
    public void persist(AppUser user) {
        em.persist(user);
    }

    @Override
    public void flush() {
        em.flush();
    }
}
