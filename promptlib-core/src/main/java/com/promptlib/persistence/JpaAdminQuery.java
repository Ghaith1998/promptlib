package com.promptlib.persistence;

import com.promptlib.domain.admin.model.AdminPromptRow;
import com.promptlib.domain.admin.port.AdminQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class JpaAdminQuery implements AdminQuery {

    @Inject
    EntityManager em;

    @Override
    public List<AdminPromptRow> listPrompts() {
        return em.createQuery("""
                        select new com.promptlib.domain.admin.model.AdminPromptRow(
                            p.id, p.ownerId, u.username, p.title, str(p.visibility), str(p.moderationState),
                            p.copyCount, p.hiddenReason, p.createdAt)
                        from Prompt p join AppUser u on u.id = p.ownerId
                        where p.deletedAt is null
                        order by p.createdAt desc
                        """, AdminPromptRow.class)
                .getResultList();
    }
}
