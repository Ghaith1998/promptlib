package com.promptlib.domain.prompt.model;

import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholders are the contract between a version's text and its typed variables.
 * Every {{name}} must be declared, and every declaration must be used.
 */
public final class VariableParser {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}");
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_VARIABLES = 100;
    private static final int MAX_TOTAL_CHARS = 1_000_000;

    private VariableParser() {
    }

    public static Set<String> placeholders(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(content == null ? "" : content);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    public static void validate(List<MessageDraft> messages, List<VariableDefinition> variables) {
        if (messages.isEmpty()) {
            throw new ValidationFailed(ProblemCode.EMPTY_PROMPT);
        }
        if (messages.size() > MAX_MESSAGES) {
            throw new ValidationFailed(ProblemCode.TOO_MANY_MESSAGES);
        }
        if (variables.size() > MAX_VARIABLES) {
            throw new ValidationFailed(ProblemCode.TOO_MANY_VARIABLES);
        }

        int total = 0;
        Set<String> used = new LinkedHashSet<>();
        for (MessageDraft message : messages) {
            if (message.content() == null || message.content().isBlank()) {
                throw new ValidationFailed(ProblemCode.EMPTY_PROMPT);
            }
            total += message.content().length();
            used.addAll(placeholders(message.content()));
        }
        if (total > MAX_TOTAL_CHARS) {
            throw new ValidationFailed(ProblemCode.CONTENT_TOO_LARGE);
        }

        Set<String> declared = new LinkedHashSet<>();
        for (VariableDefinition variable : variables) {
            if (variable.name() == null || !variable.name().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new ValidationFailed(ProblemCode.VARIABLE_MISMATCH);
            }
            if (!declared.add(variable.name())) {
                throw new ValidationFailed(ProblemCode.VARIABLE_MISMATCH);
            }
            if (variable.type() == VariableType.ENUM && variable.enumValues().isEmpty()) {
                throw new ValidationFailed(ProblemCode.VARIABLE_MISMATCH);
            }
        }

        if (!used.equals(declared)) {
            throw new ValidationFailed(ProblemCode.VARIABLE_MISMATCH,
                    "placeholders " + used + " do not match variables " + declared);
        }
    }
}
