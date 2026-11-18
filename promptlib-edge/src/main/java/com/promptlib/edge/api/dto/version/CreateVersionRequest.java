package com.promptlib.edge.api.dto.version;

import com.promptlib.edge.api.dto.prompt.MessageDto;
import com.promptlib.edge.api.dto.prompt.VariableDto;

import java.util.List;

public record CreateVersionRequest(String changeNote, List<MessageDto> messages,
                                   List<VariableDto> variables) {
}
