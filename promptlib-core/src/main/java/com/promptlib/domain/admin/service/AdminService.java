package com.promptlib.domain.admin.service;

import com.promptlib.domain.admin.model.AdminPromptRow;
import com.promptlib.domain.admin.port.AdminQuery;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.error.Forbidden;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
@Transactional
public class AdminService {

    private final AdminQuery query;

    @Inject
    public AdminService(AdminQuery query) {
        this.query = query;
    }

    public List<AdminPromptRow> listPrompts(CallerContext admin) {
        if (!admin.isAdmin()) {
            throw new Forbidden();
        }
        return query.listPrompts();
    }
}
