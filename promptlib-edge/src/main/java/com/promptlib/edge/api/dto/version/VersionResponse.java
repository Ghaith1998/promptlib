package com.promptlib.edge.api.dto.version;

import com.promptlib.edge.api.dto.prompt.MessageDto;
import com.promptlib.edge.api.dto.prompt.VariableDto;

import java.util.List;

public record VersionResponse(String id, String promptId, int versionNumber, String changeNote,
                              String createdAt, List<MessageDto> messages,
                              List<VariableDto> variables) {
}
