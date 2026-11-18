package com.promptlib.edge.api.dto.version;

public record VersionSummaryResponse(String id, int versionNumber, String changeNote, String createdAt) {
}
