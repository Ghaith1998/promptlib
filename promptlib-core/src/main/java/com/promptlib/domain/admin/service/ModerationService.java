package com.promptlib.domain.admin.service;

import com.promptlib.shared.CallerContext;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.Forbidden;
import com.promptlib.shared.error.ValidationFailed;
import com.promptlib.shared.port.PromptModeration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class ModerationService {

    private final PromptModeration prompts;
    private final AuditWriter audit;

    @Inject
    public ModerationService(PromptModeration prompts, AuditWriter audit) {
        this.prompts = prompts;
        this.audit = audit;
    }

    @Transactional
    public void hide(CallerContext admin, UUID promptId, String reason, String requestId) {
        requireAdmin(admin);
        requireReason(reason);
        prompts.hide(promptId, reason, admin.userId());
        audit.record(admin, "PROMPT_HIDDEN", "PROMPT", promptId, reason, requestId);
    }

    @Transactional
    public void unhide(CallerContext admin, UUID promptId, String requestId) {
        requireAdmin(admin);
        prompts.unhide(promptId);
        audit.record(admin, "PROMPT_UNHIDDEN", "PROMPT", promptId, null, requestId);
    }

    private void requireAdmin(CallerContext caller) {
        if (!caller.isAdmin()) {
            throw new Forbidden();
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().length() < 3 || reason.length() > 500) {
            throw new ValidationFailed(ProblemCode.VALIDATION_FAILED);
        }
    }
}
