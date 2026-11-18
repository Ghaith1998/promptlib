package com.promptlib.edge.api.dto.prompt;

import java.util.List;

public record VariableDto(String name, String type, boolean required, String defaultValue,
                          String description, List<String> enumValues) {
}
