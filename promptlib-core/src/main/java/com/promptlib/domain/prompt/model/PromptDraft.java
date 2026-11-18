package com.promptlib.domain.prompt.model;

import java.util.List;

/** Input of a create (or a new version) operation. */
public record PromptDraft(
        String title,
        String description,
        List<MessageDraft> messages,
        List<VariableDefinition> variables) {

    public PromptDraft {
        messages = messages == null ? List.of() : List.copyOf(messages);
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
