package com.promptlib.domain.admin.port;

import com.promptlib.domain.admin.model.AdminPromptRow;

import java.util.List;

public interface AdminQuery {

    List<AdminPromptRow> listPrompts();
}
