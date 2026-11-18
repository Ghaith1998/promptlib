package com.promptlib.domain.prompt.model;

import java.util.List;

/** One typed placeholder of a version. Stored in prompt_version.variables_json. */
public record VariableDefinition(
        String name,
        VariableType type,
        boolean required,
        String defaultValue,
        String description,
        List<String> enumValues) {

    public VariableDefinition {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
}
