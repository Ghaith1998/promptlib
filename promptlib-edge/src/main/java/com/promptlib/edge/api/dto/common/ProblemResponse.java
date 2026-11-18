package com.promptlib.edge.api.dto.common;

public record ProblemResponse(String type, String title, int status, String detail,
                              String instance, String code, String requestId) {
}
