package com.promptlib.edge.api.dto.prompt;

import java.util.List;

public record CreatePromptRequest(String title, String description,
                                  List<MessageDto> messages, List<VariableDto> variables) {
}
